package se.yarin.morphy.service;

import java.util.Map;
import org.slf4j.Logger;
import se.yarin.morphy.service.positions.PositionIndexUnavailableException;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
    log.warn("Bad request: {}", e.getMessage());
    return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
  }

  /** A reference database without a usable position index: it needs to be built. */
  @ExceptionHandler(PositionIndexUnavailableException.class)
  public ResponseEntity<Map<String, String>> handlePositionIndexUnavailable(
      PositionIndexUnavailableException e) {
    log.warn("Position index unavailable: {}", describe(e));
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", describe(e)));
  }

  /** An operation the database's format doesn't support, e.g. reading a format that is a stub. */
  @ExceptionHandler(UnsupportedOperationException.class)
  public ResponseEntity<Map<String, String>> handleUnsupportedOperation(
      UnsupportedOperationException e) {
    log.warn("Not supported: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of("error", e.getMessage()));
  }

  /**
   * Any other failure: logged here, while the request's ids are still in the MDC, so the event is
   * tied to the request in /api/logs (left to the servlet container, it would be logged after the
   * request is done). Spring's own exceptions, and those with a status of their own, are declined
   * by throwing them again, so they keep their status (404, 400, ...).
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) throws Exception {
    if (e instanceof ErrorResponse
        || AnnotatedElementUtils.hasAnnotation(e.getClass(), ResponseStatus.class)) {
      throw e;
    }
    log.error("Request failed: {}", describe(e), e);
    return ResponseEntity.internalServerError().body(Map.of("error", describe(e)));
  }

  /**
   * What went wrong: the exception's message, followed by those of its causes that add to it, as
   * a wrapping exception's own message rarely says why ("Failed to replace game 1: ...").
   */
  static String describe(Throwable e) {
    StringBuilder sb = new StringBuilder();
    for (Throwable t = e; t != null && t != t.getCause(); t = t.getCause()) {
      String message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
      if (sb.indexOf(message) >= 0) continue;
      if (!sb.isEmpty()) sb.append(": ");
      sb.append(message);
    }
    return sb.toString();
  }
}
