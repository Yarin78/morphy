package se.yarin.cbhlib;

public class ChessBaseUnsupportedException extends ChessBaseException {
    public ChessBaseUnsupportedException() {
    }

    public ChessBaseUnsupportedException(String message) {
        super(message);
    }

    public ChessBaseUnsupportedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ChessBaseUnsupportedException(Throwable cause) {
        super(cause);
    }

    public ChessBaseUnsupportedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
