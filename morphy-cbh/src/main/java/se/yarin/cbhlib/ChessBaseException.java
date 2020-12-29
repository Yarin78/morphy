package se.yarin.cbhlib;

public class ChessBaseException extends Exception {
    public ChessBaseException() {
    }

    public ChessBaseException(String message) {
        super(message);
    }

    public ChessBaseException(String message, Throwable cause) {
        super(message, cause);
    }

    public ChessBaseException(Throwable cause) {
        super(cause);
    }

    public ChessBaseException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
