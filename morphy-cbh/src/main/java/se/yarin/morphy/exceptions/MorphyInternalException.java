package se.yarin.morphy.exceptions;

public class MorphyInternalException extends MorphyException {
    public MorphyInternalException() {
    }

    public MorphyInternalException(String message) {
        super(message);
    }

    public MorphyInternalException(String message, Throwable cause) {
        super(message, cause);
    }

    public MorphyInternalException(Throwable cause) {
        super(cause);
    }

    public MorphyInternalException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
