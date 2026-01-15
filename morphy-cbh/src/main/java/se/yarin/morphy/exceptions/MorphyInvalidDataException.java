package se.yarin.morphy.exceptions;

/**
 * Exceptions thrown when encountering invalid or unexpected data in the database. Only thrown if
 * the database is in STRICT mode, or if a critical error is found immediately when opening the
 * database.
 */
public class MorphyInvalidDataException extends MorphyException {
  public MorphyInvalidDataException() {}

  public MorphyInvalidDataException(String message) {
    super(message);
  }

  public MorphyInvalidDataException(String message, Throwable cause) {
    super(message, cause);
  }

  public MorphyInvalidDataException(Throwable cause) {
    super(cause);
  }

  public MorphyInvalidDataException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
