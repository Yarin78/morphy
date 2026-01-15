package se.yarin.morphy.exceptions;

/** Exception thrown when reading or parsing ChessBase annotations */
public class MorphyAnnotationExecption extends MorphyException {
  public MorphyAnnotationExecption() {}

  public MorphyAnnotationExecption(String message) {
    super(message);
  }

  public MorphyAnnotationExecption(String message, Throwable cause) {
    super(message, cause);
  }

  public MorphyAnnotationExecption(Throwable cause) {
    super(cause);
  }

  public MorphyAnnotationExecption(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
