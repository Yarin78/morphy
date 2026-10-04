package se.yarin.morphy.service.logs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LogBufferTest {

  private static void log(LogBuffer buffer, String message, String requestId, String sessionId) {
    buffer.add(Instant.now(), "INFO", "test", "main", message, null, requestId, sessionId);
  }

  private static List<String> messages(List<LogEntry> entries) {
    return entries.stream().map(LogEntry::message).toList();
  }

  @Test
  void aSessionSeesItsOwnEventsAndThoseOfNoRequest() {
    LogBuffer buffer = new LogBuffer(10);
    log(buffer, "startup", null, null);
    log(buffer, "mine", "r1", "s1");
    log(buffer, "theirs", "r2", "s2");

    assertEquals(List.of("startup", "mine"), messages(buffer.entriesAfter(0, "s1")));
    assertEquals(List.of("startup", "theirs"), messages(buffer.entriesAfter(0, "s2")));
    assertEquals(List.of("startup"), messages(buffer.entriesAfter(0, null)));
  }

  @Test
  void onlyTheEventsAfterTheOneAskedFor() {
    LogBuffer buffer = new LogBuffer(10);
    log(buffer, "first", null, null);
    log(buffer, "second", null, null);
    log(buffer, "third", null, null);

    assertEquals(List.of("third"), messages(buffer.entriesAfter(2, null)));
    assertEquals(3, buffer.lastSeq());
  }

  @Test
  void theOldestEventsAreDropped() {
    LogBuffer buffer = new LogBuffer(2);
    log(buffer, "first", null, null);
    log(buffer, "second", null, null);
    log(buffer, "third", null, null);

    List<LogEntry> entries = buffer.entriesAfter(0, null);
    assertEquals(List.of("second", "third"), messages(entries));
    assertEquals(List.of(2L, 3L), entries.stream().map(LogEntry::seq).toList());
  }
}
