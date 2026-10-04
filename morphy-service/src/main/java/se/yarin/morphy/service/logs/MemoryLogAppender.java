package se.yarin.morphy.service.logs;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/** Keeps every event logged in the {@link LogBuffer}; set up in logback.xml. */
public class MemoryLogAppender extends AppenderBase<ILoggingEvent> {
  @Override
  protected void append(ILoggingEvent event) {
    LogBuffer.instance().add(event);
  }
}
