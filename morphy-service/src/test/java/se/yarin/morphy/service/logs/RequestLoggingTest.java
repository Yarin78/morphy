package se.yarin.morphy.service.logs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import se.yarin.morphy.service.GlobalExceptionHandler;

/** The events logged while handling a request carry its ids, and /api/logs shows them. */
class RequestLoggingTest {

  @RestController
  static class FailingController {
    @GetMapping("/test/fail")
    String fail() {
      throw new IllegalStateException("Something broke");
    }
  }

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new FailingController(), new LogController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter())
            .build();
  }

  @Test
  void aFailureIsLoggedWithTheRequestsIds() throws Exception {
    long before = LogBuffer.instance().lastSeq();

    mvc.perform(get("/test/fail").header("X-Request-Id", "req-1").header("X-Session-Id", "ses-1"))
        .andExpect(status().isInternalServerError())
        .andExpect(header().string("X-Request-Id", "req-1"))
        .andExpect(jsonPath("$.error").value("Something broke"));

    List<LogEntry> logged = LogBuffer.instance().entriesAfter(before, "ses-1");
    LogEntry failure =
        logged.stream().filter(e -> "req-1".equals(e.requestId())).findFirst().orElseThrow();
    assertEquals("ERROR", failure.level());
    assertEquals("ses-1", failure.sessionId());
    assertNotNull(failure.stackTrace());
    assertTrue(failure.stackTrace().contains("Something broke"));

    // Another session doesn't see it
    assertTrue(
        LogBuffer.instance().entriesAfter(before, "ses-2").stream()
            .noneMatch(e -> "req-1".equals(e.requestId())));
  }

  @Test
  void theLogsEndpointReturnsTheSessionsEvents() throws Exception {
    long before = LogBuffer.instance().lastSeq();
    mvc.perform(get("/test/fail").header("X-Request-Id", "req-2").header("X-Session-Id", "ses-2"));

    mvc.perform(
            get("/api/logs").param("after", String.valueOf(before)).header("X-Session-Id", "ses-2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries[?(@.requestId == 'req-2')].level").value("ERROR"));
  }

  @Test
  void aRequestWithoutAnIdGetsOne() throws Exception {
    mvc.perform(get("/test/fail")).andExpect(header().exists("X-Request-Id"));
  }

  @Test
  void springsOwnFailuresKeepTheirStatus() throws Exception {
    mvc.perform(get("/test/nothing-here")).andExpect(status().isNotFound());
  }
}
