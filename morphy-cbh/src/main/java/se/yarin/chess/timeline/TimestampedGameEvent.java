package se.yarin.chess.timeline;

import org.jetbrains.annotations.NotNull;

/** A {@link GameEvent} that occurs at a specific time. This class is immutable. */
public record TimestampedGameEvent(int timestamp, @NotNull GameEvent event)
    implements Comparable<TimestampedGameEvent> {
  @Override
  public int compareTo(TimestampedGameEvent o) {
    return timestamp - o.timestamp;
  }
}
