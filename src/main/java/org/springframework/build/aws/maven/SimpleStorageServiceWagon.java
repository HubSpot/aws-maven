/*
 * Copyright 2010-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.build.aws.maven;

import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.proxy.ProxyInfoProvider;
import org.apache.maven.wagon.repository.Repository;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Uri;
import software.amazon.awssdk.services.s3.crt.S3CrtHttpConfiguration;
import software.amazon.awssdk.services.s3.crt.S3CrtProxyConfiguration;
import software.amazon.awssdk.services.s3.crt.S3CrtRetryConfiguration;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An implementation of the Maven Wagon interface that allows you to access the Amazon S3 service. URLs that reference
 * the S3 service should be in the form of <code>s3://bucket.name</code>. As an example
 * <code>s3://static.springframework.org</code> would put files into the <code>static.springframework.org</code> bucket
 * on the S3 service.
 * <p/>
 * This implementation uses the <code>username</code> and <code>passphrase</code> portions of the server authentication
 * metadata for credentials.
 */
public class SimpleStorageServiceWagon extends AbstractWagon {

    private static final String KEY_FORMAT = "%s%s";

    private static final String RESOURCE_FORMAT = "%s(.*)";

    private volatile S3AsyncClient s3AsyncClient;
    private volatile S3TransferManager transferManager;

    private volatile String bucketName;

    private volatile String baseDirectory;

    /**
     * Creates a new instance of the wagon
     */
    public SimpleStorageServiceWagon() {
        super(true);
    }

    protected SimpleStorageServiceWagon(S3AsyncClient s3AsyncClient, S3TransferManager transferManager, String bucketName, String baseDirectory) {
        super(true);

        this.s3AsyncClient = s3AsyncClient;
        this.transferManager = transferManager;

        this.bucketName = bucketName;
        this.baseDirectory = baseDirectory;
    }

    @Override
    protected void connectToRepository(Repository repository, AuthenticationInfo authenticationInfo,
                                       ProxyInfoProvider proxyInfoProvider) throws AuthenticationException {

        if (this.s3AsyncClient == null) {
            synchronized (this) {
                if (this.s3AsyncClient == null) {
                    S3Uri s3Uri = S3Utils.getS3Uri(repository);

                    this.bucketName = s3Uri.bucket().get();
                    this.baseDirectory = s3Uri.key().get();

                    S3CrtProxyConfiguration.Builder proxyConfig = S3CrtProxyConfiguration.builder();
                    if (proxyInfoProvider != null) {
                        ProxyInfo proxyInfo = proxyInfoProvider.getProxyInfo("s3");
                        if (proxyInfo != null) {
                            proxyConfig = proxyConfig.host(proxyInfo.getHost()).port(proxyInfo.getPort());
                        }
                    }

                    this.s3AsyncClient = S3AsyncClient.crtBuilder()
                            .credentialsProvider(() -> new WagonAwsCredentials(authenticationInfo))
                            .retryConfiguration(S3CrtRetryConfiguration.builder()
                                    .numRetries(3)
                                    .build())
                            .httpConfiguration(S3CrtHttpConfiguration.builder()
                                    .proxyConfiguration(proxyConfig
                                            .build())
                                    .build())
                            .region(s3Uri.region().orElse(Region.US_EAST_1))
                            .build();

                    this.transferManager = S3TransferManager.builder()
                            .s3Client(s3AsyncClient)
                            .build();
                }
            }
        }
    }

    @Override
    protected void disconnectFromRepository() {
        if (this.transferManager != null) {
            this.transferManager.close();
            this.transferManager = null;
        }

        if (this.s3AsyncClient != null) {
            this.s3AsyncClient.close();
            this.s3AsyncClient = null;
        }

        this.bucketName = null;
        this.baseDirectory = null;
    }

    @Override
    protected boolean doesRemoteResourceExist(String resourceName) throws AuthorizationException, TransferFailedException {
        try {
            getObjectMetadata(resourceName).join();
            return true;
        } catch (Exception e) {
            try {
                throw AwsV2ClientExceptions.propagateForAccess(e, resourceName);
            } catch (ResourceDoesNotExistException e1) {
                // Exceptions as control flow are not great
                return false;
            }
        }
    }

    @Override
    protected boolean isRemoteResourceNewer(String resourceName, long timestamp) throws ResourceDoesNotExistException, TransferFailedException, AuthorizationException {
        try {
            Instant lastModified = getObjectMetadata(resourceName)
                    .join()
                    .lastModified();
            return lastModified == null || lastModified.toEpochMilli() > timestamp;
        } catch (Exception e) {
            throw AwsV2ClientExceptions.propagateForAccess(e, resourceName);
        }
    }

    @Override
    protected List<String> listDirectory(String directory) throws ResourceDoesNotExistException, TransferFailedException, AuthorizationException {
        final List<String> directoryContents = new ArrayList<>();

        try {
            String prefix = getKey(directory);
            Pattern pattern = Pattern.compile(String.format(RESOURCE_FORMAT, prefix));

            s3AsyncClient.listObjectsV2Paginator(b ->
                            b.bucket(this.bucketName)
                                    .prefix(prefix)
                                    .delimiter("/"))
                    .map(res -> getResourceNames(res, pattern))
                    .subscribe(directoryContents::addAll)
                    .join();

            return directoryContents;
        } catch (Exception e) {
            throw AwsV2ClientExceptions.propagateForAccess(e, directory);
        }
    }

    @Override
    protected void getResource(String resourceName, File destination, WagonS3TransferListenerAdapter transferProgress)
            throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {

        String key = getKey(resourceName);

        try {
            transferManager.downloadFile(DownloadFileRequest.builder()
                            .getObjectRequest(b -> b.bucket(this.bucketName).key(key))
                            .addTransferListener(transferProgress)
                            .destination(destination)
                            .build())
                    .completionFuture()
                    .join();

        } catch (Exception e) {
            throw AwsV2ClientExceptions.propagateForRead(e, key);
        }
    }

    @Override
    protected void putResource(File source, String destination, WagonS3TransferListenerAdapter transferProgress)
            throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        String key = getKey(destination);

        try {
            transferManager.uploadFile(UploadFileRequest.builder()
                            .putObjectRequest(b -> b.bucket(this.bucketName)
                                    .key(key))
                            .addTransferListener(transferProgress)
                            .source(source)
                            .build())
                    .completionFuture()
                    .join();
        } catch (Exception e) {
            throw AwsV2ClientExceptions.propagateForWrite(e, destination);
        }
    }

    private CompletableFuture<HeadObjectResponse> getObjectMetadata(String resourceName) {
        return s3AsyncClient.headObject(HeadObjectRequest.builder()
                .bucket(this.bucketName)
                .key(getKey(resourceName))
                .build());
    }

    private String getKey(String resourceName) {
        return String.format(KEY_FORMAT, this.baseDirectory, resourceName);
    }

    private List<String> getResourceNames(ListObjectsV2Response objectListing, Pattern pattern) {
        List<String> resourceNames = new ArrayList<String>();

        for (CommonPrefix commonPrefix : objectListing.commonPrefixes()) {
            resourceNames.add(getResourceName(commonPrefix.prefix(), pattern));
        }

        for (S3Object s3ObjectSummary : objectListing.contents()) {
            resourceNames.add(getResourceName(s3ObjectSummary.key(), pattern));
        }

        return resourceNames;
    }

    private String getResourceName(String key, Pattern pattern) {
        Matcher matcher = pattern.matcher(key);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return key;
    }

}
