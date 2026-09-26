package se.yarin.morphy.cb2;

/** Thrown when a database file holds data that doesn't follow the v2 format. */
public class InvalidDataException extends RuntimeException {
  public InvalidDataException(String message) {
    super(message);
  }

  public InvalidDataException(String message, Throwable cause) {
    super(message, cause);
  }
}
