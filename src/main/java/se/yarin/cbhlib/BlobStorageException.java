package se.yarin.cbhlib;

public class BlobStorageException extends Exception {
    public BlobStorageException() {
    }

    public BlobStorageException(String message) {
        super(message);
    }

    public BlobStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public BlobStorageException(Throwable cause) {
        super(cause);
    }

    public BlobStorageException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
