package se.yarin.morphy.service.positions;

/** A reference database whose position index is missing, out of date or can't be read. */
public class PositionIndexUnavailableException extends RuntimeException {
  public PositionIndexUnavailableException(String message) {
    super(message);
  }

  public PositionIndexUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
