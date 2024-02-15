package org.springframework.build.aws.maven;

import org.apache.maven.wagon.resource.Resource;
import software.amazon.awssdk.transfer.s3.progress.TransferListener;

class WagonS3TransferListenerAdapter implements TransferListener {


    private final Resource resource;

    private final int requestType;

    private final TransferListenerSupport transferListenerSupport;

    public WagonS3TransferListenerAdapter(Resource resource, int requestType, TransferListenerSupport transferListenerSupport) {
        this.resource = resource;
        this.requestType = requestType;
        this.transferListenerSupport = transferListenerSupport;
    }

    @Override
    public void transferInitiated(Context.TransferInitiated context) {
        transferListenerSupport.fireTransferStarted(resource, requestType);
    }

    @Override
    public void bytesTransferred(Context.BytesTransferred context) {
    }

    public void notify(byte[] b, int len) {
        transferListenerSupport.fireTransferProgress(resource, requestType, b, len);
    }

    @Override
    public void transferComplete(Context.TransferComplete context) {
        transferListenerSupport.fireTransferCompleted(resource, requestType);
    }

    @Override
    public void transferFailed(Context.TransferFailed context) {
        transferListenerSupport.fireTransferError(resource, requestType,
                wrapThrowable(context.exception()));
    }

    private static Exception wrapThrowable(Throwable t) {
        if (t instanceof Exception) {
            return (Exception) t;
        } else {
            return new RuntimeException(t);
        }
    }
}
