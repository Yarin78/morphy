package yarin.cbhlib.exceptions;

/**
 * Exception thrown when one of the files in a CBH database wasn't found or couldn't be opened.
 */
public class CBHIOException extends CBHException {
    public CBHIOException() {
    }

    public CBHIOException(String message) {
        super(message);
    }

    public CBHIOException(String message, Throwable cause) {
        super(message, cause);
    }

    public CBHIOException(Throwable cause) {
        super(cause);
    }
}
