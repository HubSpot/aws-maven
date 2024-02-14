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

import org.apache.maven.wagon.repository.Repository;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Uri;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class S3Utils {

    private static final Pattern S3_VHOST_DOMAIN_PATTERN =
            Pattern.compile("([\\w\\-]+)\\.s3\\.([\\w\\-]+)\\.amazonaws\\.com");

    private S3Utils() {
    }

    static S3Uri getS3Uri(Repository repository) {
        S3Uri.Builder uri = S3Uri.builder()
                .uri(URI.create(repository.getUrl()));

        Matcher hostMatcher = S3_VHOST_DOMAIN_PATTERN.matcher(repository.getHost());

        if (hostMatcher.matches()) {
            uri = uri.bucket(hostMatcher.group(1))
                    .region(Region.of(hostMatcher.group(2)));
        } else {
            uri = uri.bucket(repository.getHost());
        }

        StringBuilder sb = new StringBuilder(repository.getBasedir()).deleteCharAt(0);

        if ((sb.length() != 0) && (sb.charAt(sb.length() - 1) != '/')) {
            sb.append('/');
        }

        uri = uri.key(sb.toString());

        return uri.build();
    }

}
