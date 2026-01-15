package se.yarin.chess.timeline;

import org.jetbrains.annotations.NotNull;

/** A {@link GameEvent} that occurs at a specific time. This class is immutable. */
public class TimestampedGameEvent implements Comparable<TimestampedGameEvent> {
  private final int timestamp;
  private final GameEvent event;

  public int getTimestamp() {
    return timestamp;
  }

  public GameEvent getEvent() {
    return event;
  }

  public TimestampedGameEvent(int timestamp, @NotNull GameEvent event) {
    this.timestamp = timestamp;
    this.event = event;
  }

  @Override
  public int compareTo(TimestampedGameEvent o) {
    return timestamp - o.timestamp;
  }
}
