package org.springframework.build.aws.maven;

import org.apache.maven.wagon.authentication.AuthenticationInfo;
import software.amazon.awssdk.auth.credentials.AwsCredentials;

public class WagonAwsCredentials implements AwsCredentials {

    private final AuthenticationInfo wagonAuthInfo;

    public WagonAwsCredentials(AuthenticationInfo wagonAuthInfo) {
        this.wagonAuthInfo = wagonAuthInfo;
    }

    @Override
    public String accessKeyId() {
        return wagonAuthInfo.getUserName();
    }

    @Override
    public String secretAccessKey() {
        return wagonAuthInfo.getPassword();
    }

}
