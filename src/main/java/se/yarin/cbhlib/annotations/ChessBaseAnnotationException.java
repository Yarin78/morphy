package se.yarin.cbhlib.annotations;

/**
 * Exception thrown when reading or parsing ChessBase annotations
 */
public class ChessBaseAnnotationException extends Exception {
    public ChessBaseAnnotationException() {
    }

    public ChessBaseAnnotationException(String message) {
        super(message);
    }

    public ChessBaseAnnotationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ChessBaseAnnotationException(Throwable cause) {
        super(cause);
    }

    public ChessBaseAnnotationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
