package se.yarin.cbhlib.exceptions;

public class ChessBaseMissingGameException extends RuntimeException {
    public ChessBaseMissingGameException() {
    }

    public ChessBaseMissingGameException(String message) {
        super(message);
    }

    public ChessBaseMissingGameException(String message, Throwable cause) {
        super(message, cause);
    }

    public ChessBaseMissingGameException(Throwable cause) {
        super(cause);
    }

    public ChessBaseMissingGameException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
