package se.yarin.morphy.exceptions;

/**
 * Exception thrown if there there are critical integrity issues with an EntityIndex
 */
public class MorphyEntityIndexException extends MorphyException {
    public MorphyEntityIndexException() {
    }

    public MorphyEntityIndexException(String message) {
        super(message);
    }

    public MorphyEntityIndexException(String message, Throwable cause) {
        super(message, cause);
    }

    public MorphyEntityIndexException(Throwable cause) {
        super(cause);
    }

    public MorphyEntityIndexException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
