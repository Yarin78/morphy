package se.yarin.morphy.service.logs;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The most recent events the service logged, kept in memory for /api/logs. The events logged
 * while handling a request carry its request and session ids (see {@link RequestIdFilter}),
 * so a client is shown the events of its own requests, plus those of no request (startup, a
 * database opening in the background).
 *
 * <p>The buffer is shared, as logback creates the appender that fills it, not Spring.
 */
public class LogBuffer {
  /** The events kept; older ones are dropped. */
  public static final int CAPACITY = 5000;

  private static final LogBuffer INSTANCE = new LogBuffer(CAPACITY);

  private final int capacity;
  private final ArrayDeque<LogEntry> entries = new ArrayDeque<>();
  private long nextSeq = 1;

  LogBuffer(int capacity) {
    this.capacity = capacity;
  }

  public static @NotNull LogBuffer instance() {
    return INSTANCE;
  }

  /** Keeps a logged event. */
  public void add(@NotNull ILoggingEvent event) {
    Map<String, String> mdc = event.getMDCPropertyMap();
    add(
        Instant.ofEpochMilli(event.getTimeStamp()),
        event.getLevel().toString(),
        event.getLoggerName(),
        event.getThreadName(),
        event.getFormattedMessage(),
        stackTrace(event.getThrowableProxy()),
        mdc.get(RequestIdFilter.REQUEST_ID),
        mdc.get(RequestIdFilter.SESSION_ID));
  }

  synchronized void add(
      @NotNull Instant time,
      @NotNull String level,
      @NotNull String logger,
      @NotNull String thread,
      @NotNull String message,
      @Nullable String stackTrace,
      @Nullable String requestId,
      @Nullable String sessionId) {
    entries.addLast(
        new LogEntry(
            nextSeq++,
            time.toString(),
            level,
            logger,
            thread,
            message,
            stackTrace,
            requestId,
            sessionId));
    while (entries.size() > capacity) {
      entries.removeFirst();
    }
  }

  /**
   * The events after the one with this seq that a session may see: those of its own requests and
   * those of no request, oldest first.
   */
  public synchronized @NotNull List<LogEntry> entriesAfter(long seq, @Nullable String sessionId) {
    List<LogEntry> result = new ArrayList<>();
    for (LogEntry entry : entries) {
      if (entry.seq() <= seq) continue;
      if (entry.sessionId() == null || entry.sessionId().equals(sessionId)) result.add(entry);
    }
    return result;
  }

  /** The seq of the last event kept, or 0 if none. */
  public synchronized long lastSeq() {
    return nextSeq - 1;
  }

  private static @Nullable String stackTrace(@Nullable IThrowableProxy throwable) {
    return throwable == null ? null : ThrowableProxyUtil.asString(throwable);
  }
}
