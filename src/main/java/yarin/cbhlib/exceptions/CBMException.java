package yarin.cbhlib.exceptions;

public class CBMException extends Exception {
    public CBMException() {
    }

    public CBMException(String message) {
        super(message);
    }

    public CBMException(String message, Throwable cause) {
        super(message, cause);
    }

    public CBMException(Throwable cause) {
        super(cause);
    }

    public CBMException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
