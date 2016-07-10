package se.yarin.cbhlib;

public class EntityStorageException extends Exception {
    public EntityStorageException() {
    }

    public EntityStorageException(String message) {
        super(message);
    }

    public EntityStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public EntityStorageException(Throwable cause) {
        super(cause);
    }

    public EntityStorageException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
