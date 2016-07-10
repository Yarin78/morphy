package se.yarin.cbhlib;

/**
 * Exception thrown internally when processing a {@link java.util.stream.Stream} of entities.
 */
public class UncheckedEntityException extends RuntimeException {
    public UncheckedEntityException() {
    }

    public UncheckedEntityException(String message) {
        super(message);
    }

    public UncheckedEntityException(String message, Throwable cause) {
        super(message, cause);
    }

    public UncheckedEntityException(Throwable cause) {
        super(cause);
    }

    public UncheckedEntityException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
