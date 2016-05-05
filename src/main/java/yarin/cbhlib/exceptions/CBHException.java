package yarin.cbhlib.exceptions;

/**
 * Exception thrown when an unexpected error occurred reading from or writing to a CBH database
 */
public class CBHException extends Exception {
    public CBHException() {
        super();
    }

    public CBHException(String message) {
        super(message);
    }

    public CBHException(String message, Throwable cause) {
        super(message, cause);
    }

    public CBHException(Throwable cause) {
        super(cause);
    }
}
