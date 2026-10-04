package se.yarin.morphy.service.logs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An event the service logged, as /api/logs returns it.
 *
 * @param seq the event's place among all events kept, increasing; a client asks for the events
 *     after the last one it has
 * @param time when it was logged, as an ISO-8601 instant
 * @param level TRACE, DEBUG, INFO, WARN or ERROR
 * @param stackTrace the exception logged with the event, if any
 * @param requestId the request the event was logged while handling, or null if none
 * @param sessionId the client session that request came from, or null if none
 */
public record LogEntry(
    long seq,
    @NotNull String time,
    @NotNull String level,
    @NotNull String logger,
    @NotNull String thread,
    @NotNull String message,
    @Nullable String stackTrace,
    @Nullable String requestId,
    @Nullable String sessionId) {}
