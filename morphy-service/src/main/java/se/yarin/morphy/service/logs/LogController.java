package se.yarin.morphy.service.logs;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The service's recent log events, for a client to show: its own requests', and the service's. */
@RestController
@RequestMapping("/api/logs")
public class LogController {

  /**
   * The events logged after the one numbered {@code after}: while handling the requests of the
   * caller's session (X-Session-Id), or outside any request.
   *
   * @param after the seq of the last event the caller has; 0 for all
   */
  @GetMapping
  public LogsResponse getLogs(
      @RequestParam(defaultValue = "0") long after,
      @RequestHeader(name = RequestIdFilter.SESSION_ID_HEADER, required = false) @Nullable
          String sessionId) {
    LogBuffer buffer = LogBuffer.instance();
    return new LogsResponse(
        buffer.entriesAfter(after, RequestIdFilter.validId(sessionId)), buffer.lastSeq());
  }

  /**
   * @param lastSeq the seq of the last event the service has, to ask for the events after next
   */
  public record LogsResponse(List<LogEntry> entries, long lastSeq) {}
}
