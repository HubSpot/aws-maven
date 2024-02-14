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

import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.proxy.ProxyInfoProvider;
import org.apache.maven.wagon.repository.Repository;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public final class S3UtilsTest {

    private static final int PORT = 100;

    private final ProxyInfoProvider proxyInfoProvider = mock(ProxyInfoProvider.class);

    private final ProxyInfo proxyInfo = mock(ProxyInfo.class);

    @Test
    public void getBucketName() {
        assertThat(S3Utils.getS3Uri(createRepository("/")).bucket())
                .hasValue("dist.springsource.com");

        assertThat(S3Utils.getS3Uri(createRepository("my-bucket", "/artifacts/snapshots")).bucket())
                .hasValue("my-bucket");
        assertThat(S3Utils.getS3Uri(createRepository("my-bucket.s3.us-west-2.amazonaws.com", "/artifacts/snapshots")).bucket())
                .hasValue("my-bucket");
    }

    @Test
    public void getBucketRegion() {
        assertThat(S3Utils.getS3Uri(createRepository("/")).region()).isEmpty();

        assertThat(S3Utils.getS3Uri(createRepository("my-bucket", "/artifacts/snapshots")).region())
                .isEmpty();
        assertThat(S3Utils.getS3Uri(createRepository("my-bucket.s3.us-west-2.amazonaws.com", "/artifacts/snapshots")).region())
                .hasValue(Region.US_WEST_2);
    }

    @Test
    public void getBaseDirectory() {
        assertThat(S3Utils.getS3Uri(createRepository("")).key())
                .hasValue("");
        assertThat(S3Utils.getS3Uri(createRepository("/")).key())
                .hasValue("");

        assertThat(S3Utils.getS3Uri(createRepository("/foo")).key())
                .hasValue("foo/");
        assertThat(S3Utils.getS3Uri(createRepository("/foo/")).key())
                .hasValue("foo/");

        assertThat(S3Utils.getS3Uri(createRepository("/foo/bar")).key())
                .hasValue("foo/bar/");
        assertThat(S3Utils.getS3Uri(createRepository("/foo/bar/")).key())
                .hasValue("foo/bar/");
    }

    private Repository createRepository(String path) {
        return createRepository("dist.springsource.com", path);
    }

    private Repository createRepository(String host, String path) {
        return new Repository("foo", String.format("s3://%s%s", host, path));
    }
}
