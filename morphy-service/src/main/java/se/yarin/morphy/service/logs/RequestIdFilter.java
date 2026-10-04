package se.yarin.morphy.service.logs;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tags everything logged while handling a request with the request's id and the id of the client
 * session it came from, which the client sends in the X-Request-Id and X-Session-Id headers. A
 * request without an id gets one; the response returns it. Later, a user id would go along with
 * them, for the logs to be filtered by user instead of session.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
  /** The MDC keys, as logback hands them to {@link LogBuffer} */
  public static final String REQUEST_ID = "requestId";

  public static final String SESSION_ID = "sessionId";

  public static final String REQUEST_ID_HEADER = "X-Request-Id";
  public static final String SESSION_ID_HEADER = "X-Session-Id";

  // Ids come from the client, so only short, plain ones are taken
  private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

  @Override
  protected void doFilterInternal(
      @NotNull HttpServletRequest request,
      @NotNull HttpServletResponse response,
      @NotNull FilterChain chain)
      throws ServletException, IOException {
    String requestId = validId(request.getHeader(REQUEST_ID_HEADER));
    if (requestId == null) requestId = UUID.randomUUID().toString();
    String sessionId = validId(request.getHeader(SESSION_ID_HEADER));
    MDC.put(REQUEST_ID, requestId);
    if (sessionId != null) MDC.put(SESSION_ID, sessionId);
    response.setHeader(REQUEST_ID_HEADER, requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(REQUEST_ID);
      MDC.remove(SESSION_ID);
    }
  }

  static @Nullable String validId(@Nullable String id) {
    return id != null && ID.matcher(id).matches() ? id : null;
  }
}
