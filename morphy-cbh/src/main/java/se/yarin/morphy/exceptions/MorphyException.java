package se.yarin.morphy.exceptions;

/**
 * Base class for exceptions thrown by Morphy library
 */
public class MorphyException extends RuntimeException {
    public MorphyException() {
    }

    public MorphyException(String message) {
        super(message);
    }

    public MorphyException(String message, Throwable cause) {
        super(message, cause);
    }

    public MorphyException(Throwable cause) {
        super(cause);
    }

    public MorphyException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
