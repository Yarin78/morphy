package se.yarin.cbhlib.exceptions;

/**
 * Unchecked exception wrapping IOExceptions thrown when reading and writing
 * to a ChessBase database after it's been opened.
 */
public class ChessBaseIOException extends RuntimeException {
    public ChessBaseIOException() {
    }

    public ChessBaseIOException(String message) {
        super(message);
    }

    public ChessBaseIOException(String message, Throwable cause) {
        super(message, cause);
    }

    public ChessBaseIOException(Throwable cause) {
        super(cause);
    }

    public ChessBaseIOException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
