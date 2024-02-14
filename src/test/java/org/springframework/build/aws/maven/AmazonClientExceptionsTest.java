package org.springframework.build.aws.maven;

import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class AmazonClientExceptionsTest {

    @Test
    public void itThrowsATransferFailedExceptionOnANonServiceException() throws Exception {
        assertThrows(TransferFailedException.class, () -> {
                    AwsV2ClientExceptions.propagateForAccess(SdkClientException.create("Test message"), "some/key");
                }
        );
    }

    @Test
    public void itThrowsAnAuthorizationExceptionFor403() throws Exception {
        assertThrows(AuthorizationException.class, () -> {
            createAndPropagateException(403, "AccessDenied", "some/key");
        });
    }

    @Test
    public void itThrowsAResourceDoesNotExistExceptionOn403() throws Exception {
        assertThrows(ResourceDoesNotExistException.class, () -> {
            createAndPropagateException(404, "NoSuchKey", "some/key");
        });
    }

    @Test
    public void itThrowsATransferFailedExceptionOn500() throws Exception {
        assertThrows(TransferFailedException.class, () -> {
            createAndPropagateException(500, "InternalError", "some/key");
        });
    }

    private void createAndPropagateException(
            int statusCode,
            String errorCode,
            String s3Key
    ) throws Exception {
        AwsServiceException amazonServiceException = AwsServiceException.builder()
                .statusCode(statusCode)
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode(errorCode).build())
                .message(String.format("%d: %s", statusCode, errorCode))
                .build();

        AwsV2ClientExceptions.propagateForAccess(amazonServiceException, s3Key);
    }
}
