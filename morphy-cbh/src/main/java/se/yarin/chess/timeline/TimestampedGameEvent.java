package se.yarin.chess.timeline;

import lombok.Getter;
import lombok.NonNull;

/**
 * A {@link GameEvent} that occurs at a specific time.
 * This class is immutable.
 */
public class TimestampedGameEvent implements Comparable<TimestampedGameEvent> {
    @Getter private int timestamp;
    @Getter private GameEvent event;

    public TimestampedGameEvent(int timestamp, @NonNull GameEvent event) {
        this.timestamp = timestamp;
        this.event = event;
    }

    @Override
    public int compareTo(TimestampedGameEvent o) {
        return timestamp - o.timestamp;
    }
}
