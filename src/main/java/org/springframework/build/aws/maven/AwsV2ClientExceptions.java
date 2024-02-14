package org.springframework.build.aws.maven;

import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

import java.util.concurrent.CompletionException;

class AwsV2ClientExceptions {

    public static RuntimeException propagateForRead(
            Throwable amazonClientException,
            String s3Key
    ) throws AuthorizationException, ResourceDoesNotExistException, TransferFailedException {
        return propagate(amazonClientException, "Error reading '%s'", s3Key);
    }

    public static RuntimeException propagateForWrite(
            Throwable amazonClientException,
            String s3Key
    ) throws AuthorizationException, ResourceDoesNotExistException, TransferFailedException {
        return propagate(amazonClientException, "Error writing '%s'", s3Key);
    }

    public static RuntimeException propagateForAccess(
            Throwable amazonClientException,
            String s3Key
    ) throws AuthorizationException, ResourceDoesNotExistException, TransferFailedException {
        return propagate(amazonClientException, "Error accessing '%s'", s3Key);
    }

    private static RuntimeException propagate(
            Throwable amazonClientException, String errorMessageTemplate,
            String s3Key
    ) throws AuthorizationException, ResourceDoesNotExistException, TransferFailedException {
        Throwable unwrapped = unwrapCompletionException(amazonClientException);

        if (!(unwrapped instanceof AwsServiceException)) {
            throw new TransferFailedException(
                    String.format(errorMessageTemplate, s3Key),
                    unwrapped);
        }
        switch (((AwsServiceException) unwrapped).statusCode()) {
            case 403:
                throw new AuthorizationException(
                        String.format(errorMessageTemplate, s3Key),
                        unwrapped);
            case 404:
                throw new ResourceDoesNotExistException(
                        String.format("'%s' does not exist", s3Key),
                        unwrapped);
            default:
                throw new TransferFailedException(
                        String.format(errorMessageTemplate, s3Key),
                        unwrapped);
        }
    }

    private static Throwable unwrapCompletionException(Throwable t) {
        if (t instanceof CompletionException) {
            return t.getCause();
        } else {
            return t;
        }
    }

    private AwsV2ClientExceptions() {
        throw new AssertionError();
    }

}
