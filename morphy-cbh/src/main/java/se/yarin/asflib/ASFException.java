package se.yarin.asflib;

import java.io.IOException;

public class ASFException extends IOException {
    public ASFException() {
    }

    public ASFException(String message) {
        super(message);
    }

    public ASFException(String message, Throwable cause) {
        super(message, cause);
    }

    public ASFException(Throwable cause) {
        super(cause);
    }
}
