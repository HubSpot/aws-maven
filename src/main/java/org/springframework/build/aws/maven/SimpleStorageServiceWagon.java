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

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.internal.Mimetypes;
import com.amazonaws.services.s3.model.HeadBucketRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.proxy.ProxyInfoProvider;
import org.apache.maven.wagon.repository.Repository;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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

    private volatile AmazonS3 amazonS3;

    private volatile String bucketName;

    private volatile String baseDirectory;

    /**
     * Creates a new instance of the wagon
     */
    public SimpleStorageServiceWagon() {
        super(true);
    }

    protected SimpleStorageServiceWagon(AmazonS3 amazonS3, String bucketName, String baseDirectory) {
        super(true);
        this.amazonS3 = amazonS3;
        this.bucketName = bucketName;
        this.baseDirectory = baseDirectory;
    }

    @Override
    protected void connectToRepository(Repository repository, AuthenticationInfo authenticationInfo,
                                       ProxyInfoProvider proxyInfoProvider) throws AuthenticationException {
        if (this.amazonS3 == null) {
            this.bucketName = S3Utils.getBucketName(repository);
            this.baseDirectory = S3Utils.getBaseDirectory(repository);

            AmazonS3ClientBuilder amazonS3ClientBuilder = AmazonS3ClientBuilder.standard()
                    .withCredentials(new AuthenticationInfoAWSCredentialsProvider(authenticationInfo))
                    .withClientConfiguration(S3Utils.getClientConfiguration(proxyInfoProvider));

            String bucketRegion = amazonS3ClientBuilder.build()
                    .headBucket(new HeadBucketRequest(this.bucketName))
                    .getBucketRegion();

            this.amazonS3 = amazonS3ClientBuilder.withRegion(bucketRegion).build();
        }
    }

    @Override
    protected void disconnectFromRepository() {
        this.amazonS3 = null;
        this.bucketName = null;
        this.baseDirectory = null;
    }

    @Override
    protected boolean doesRemoteResourceExist(String resourceName) throws AuthorizationException, TransferFailedException {
        try {
            getObjectMetadata(resourceName);
            return true;
        } catch (AmazonClientException e) {
            try {
                throw AmazonClientExceptions.propagateForAccess(e, resourceName);
            } catch (ResourceDoesNotExistException e1) {
                // Exceptions as control flow are not great
                return false;
            }
        }
    }

    @Override
    protected boolean isRemoteResourceNewer(String resourceName, long timestamp) throws ResourceDoesNotExistException, TransferFailedException, AuthorizationException {
        try {
            Date lastModified = getObjectMetadata(resourceName).getLastModified();
            return lastModified == null || lastModified.getTime() > timestamp;
        } catch (AmazonClientException e) {
            throw AmazonClientExceptions.propagateForAccess(e, resourceName);
        }
    }

    @Override
    protected List<String> listDirectory(String directory) throws ResourceDoesNotExistException, TransferFailedException, AuthorizationException {
        List<String> directoryContents = new ArrayList<String>();

        try {
            String prefix = getKey(directory);
            Pattern pattern = Pattern.compile(String.format(RESOURCE_FORMAT, prefix));

            ListObjectsRequest listObjectsRequest = new ListObjectsRequest() //
                    .withBucketName(this.bucketName) //
                    .withPrefix(prefix) //
                    .withDelimiter("/");

            ObjectListing objectListing;

            objectListing = this.amazonS3.listObjects(listObjectsRequest);
            directoryContents.addAll(getResourceNames(objectListing, pattern));

            while (objectListing.isTruncated()) {
                objectListing = this.amazonS3.listObjects(listObjectsRequest);
                directoryContents.addAll(getResourceNames(objectListing, pattern));
            }

            return directoryContents;
        } catch (AmazonClientException e) {
            throw AmazonClientExceptions.propagateForAccess(e, directory);
        }
    }

    @Override
    protected void getResource(String resourceName, File destination, TransferProgress transferProgress)
            throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        InputStream in = null;
        OutputStream out = null;
        try {
            S3Object s3Object = this.amazonS3.getObject(this.bucketName, getKey(resourceName));

            in = s3Object.getObjectContent();
            out = new TransferProgressFileOutputStream(destination, transferProgress);

            transferProgress.startTransferAttempt();
            IoUtils.copy(in, out);
        } catch (AmazonClientException e) {
            throw AmazonClientExceptions.propagateForRead(e, resourceName);
        } catch (FileNotFoundException e) {
            throw new TransferFailedException(String.format("Cannot write file to '%s'", destination), e);
        } catch (IOException e) {
            throw new TransferFailedException(String.format("Cannot read from '%s' and write to '%s'", resourceName, destination), e);
        } finally {
            IoUtils.closeQuietly(in, out);
        }
    }

    @Override
    protected void putResource(File source, String destination, TransferProgress transferProgress)
            throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        String key = getKey(destination);

        InputStream in = null;
        try {
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(source.length());
            objectMetadata.setContentType(Mimetypes.getInstance().getMimetype(source));

            in = new TransferProgressFileInputStream(source, transferProgress);

            transferProgress.startTransferAttempt();
            this.amazonS3.putObject(new PutObjectRequest(this.bucketName, key, in, objectMetadata));
        } catch (AmazonClientException e) {
            throw AmazonClientExceptions.propagateForWrite(e, destination);
        } catch (FileNotFoundException e) {
            throw new ResourceDoesNotExistException(String.format("Cannot read file from '%s'", source), e);
        } finally {
            IoUtils.closeQuietly(in);
        }
    }

    private ObjectMetadata getObjectMetadata(String resourceName) {
        return this.amazonS3.getObjectMetadata(this.bucketName, getKey(resourceName));
    }

    private String getKey(String resourceName) {
        return String.format(KEY_FORMAT, this.baseDirectory, resourceName);
    }

    private List<String> getResourceNames(ObjectListing objectListing, Pattern pattern) {
        List<String> resourceNames = new ArrayList<String>();

        for (String commonPrefix : objectListing.getCommonPrefixes()) {
            resourceNames.add(getResourceName(commonPrefix, pattern));
        }

        for (S3ObjectSummary s3ObjectSummary : objectListing.getObjectSummaries()) {
            resourceNames.add(getResourceName(s3ObjectSummary.getKey(), pattern));
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
