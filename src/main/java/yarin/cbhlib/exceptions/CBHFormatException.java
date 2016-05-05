package yarin.cbhlib.exceptions;

/**
 * Exception thrown when some data in a CBH file was unexpected.
 */
public class CBHFormatException extends CBHException {
    public CBHFormatException() {
    }

    public CBHFormatException(String message) {
        super(message);
    }

    public CBHFormatException(String message, Throwable cause) {
        super(message, cause);
    }

    public CBHFormatException(Throwable cause) {
        super(cause);
    }
}
