package se.yarin.morphy.exceptions;

/**
 * Thrown if the library couldn't carry out an action because the feature is not (yet?!) supported.
 */
public class MorphyNotSupportedException extends MorphyException {
    public MorphyNotSupportedException() {
    }

    public MorphyNotSupportedException(String message) {
        super(message);
    }

    public MorphyNotSupportedException(String message, Throwable cause) {
        super(message, cause);
    }

    public MorphyNotSupportedException(Throwable cause) {
        super(cause);
    }

    public MorphyNotSupportedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
