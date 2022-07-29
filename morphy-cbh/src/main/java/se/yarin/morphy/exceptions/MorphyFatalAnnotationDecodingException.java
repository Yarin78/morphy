package se.yarin.morphy.exceptions;

public class MorphyFatalAnnotationDecodingException extends MorphyAnnotationExecption {
    public MorphyFatalAnnotationDecodingException() {
    }

    public MorphyFatalAnnotationDecodingException(String message) {
        super(message);
    }

    public MorphyFatalAnnotationDecodingException(String message, Throwable cause) {
        super(message, cause);
    }

    public MorphyFatalAnnotationDecodingException(Throwable cause) {
        super(cause);
    }

    public MorphyFatalAnnotationDecodingException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
