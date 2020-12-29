package se.yarin.cbhlib.media;

/**
 * Exception thrown when something went wrong with loading or parsing
 * a ChessBase media file.
 */
public class ChessBaseMediaException extends Exception {
    public ChessBaseMediaException() {
    }

    public ChessBaseMediaException(String message) {
        super(message);
    }

    public ChessBaseMediaException(String message, Throwable cause) {
        super(message, cause);
    }

    public ChessBaseMediaException(Throwable cause) {
        super(cause);
    }

    public ChessBaseMediaException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
