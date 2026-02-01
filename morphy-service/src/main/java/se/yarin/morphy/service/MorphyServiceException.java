package se.yarin.morphy.service;

/**
 * Exception thrown when errors occur in the Morphy service layer.
 */
public class MorphyServiceException extends RuntimeException {
  public MorphyServiceException(String message) {
    super(message);
  }

  public MorphyServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}
