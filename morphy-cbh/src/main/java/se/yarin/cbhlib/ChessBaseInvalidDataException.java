package se.yarin.cbhlib;

public class ChessBaseInvalidDataException extends ChessBaseException {
    public ChessBaseInvalidDataException() {
    }

    public ChessBaseInvalidDataException(String message) {
        super(message);
    }

    public ChessBaseInvalidDataException(String message, Throwable cause) {
        super(message, cause);
    }

    public ChessBaseInvalidDataException(Throwable cause) {
        super(cause);
    }

    public ChessBaseInvalidDataException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
