package se.yarin.cbhlib.storage;

public class EntityStorageDuplicateKeyException extends EntityStorageException {
    public EntityStorageDuplicateKeyException() {
    }

    public EntityStorageDuplicateKeyException(String message) {
        super(message);
    }

    public EntityStorageDuplicateKeyException(String message, Throwable cause) {
        super(message, cause);
    }

    public EntityStorageDuplicateKeyException(Throwable cause) {
        super(cause);
    }

    public EntityStorageDuplicateKeyException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
