package se.yarin.morphy.exceptions;

/**
 * Unchecked exception wrapping IOExceptions thrown when reading and writing
 * to a database after it's been successfully opened.
 */
public class MorphyIOException extends MorphyException {
    public MorphyIOException() {
    }

    public MorphyIOException(String message) {
        super(message);
    }

    public MorphyIOException(String message, Throwable cause) {
        super(message, cause);
    }

    public MorphyIOException(Throwable cause) {
        super(cause);
    }

    public MorphyIOException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
