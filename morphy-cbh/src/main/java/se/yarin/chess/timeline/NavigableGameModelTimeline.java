package se.yarin.chess.timeline;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.NavigableGameModel;

import java.util.*;

/**
 * A {@link NavigableGameModel} with a timeline of events ({@link GameEvent}) and a timeline cursor.
 * The underlying {@link NavigableGameModel} can be mutated by changing the timeline cursor using
 * {@link #playTo(int)} or {@link #jumpTo(int)}.
 */
public class NavigableGameModelTimeline {
  private static final Logger log = LoggerFactory.getLogger(NavigableGameModelTimeline.class);

  private NavigableGameModel model;
  private List<TimestampedGameEvent> events = new ArrayList<>();

  // These two fields goes hand-in-hand. The indexOfLastAppliedEvent is the important one,
  // since there may be multiple events with the same timestamp.
  // However, it's more natural to expose the timestampe in the API.
  private int indexOfLastAppliedEvent = -1;
  private int currentTimestamp = 0;

  public NavigableGameModelTimeline() {
    this.model = new NavigableGameModel();
    this.events = new ArrayList<>(events);
  }

  /**
   * @return the underlying {@link NavigableGameModel}
   */
  public NavigableGameModel getModel() {
    return model;
  }

  /**
   * @return the current timestamp
   */
  public int getCurrentTimestamp() {
    return currentTimestamp;
  }

  private static String formatMillis(int millis) {
    return String.format("%d:%02d", millis / 1000 / 60, millis / 1000 % 60);
  }

  /**
   * Gets the timestamp of the last event in the timeline
   *
   * @return the last event timestamp
   */
  public int getLastEventTimestamp() {
    if (events.size() == 0) return 0;
    return events.get(events.size() - 1).getTimestamp();
  }

  /**
   * Gets the timestamp of the next event in the timeline that hasn't been applied
   *
   * @return the next event timestamps, or Integer.MAX_VALUE if there are no more events to be
   *     applied
   */
  public int getNextEventTimestamp() {
    if (indexOfLastAppliedEvent + 1 >= events.size()) {
      return Integer.MAX_VALUE;
    }
    return events.get(indexOfLastAppliedEvent + 1).getTimestamp();
  }

  /**
   * Gets the next event to apply. The model is not changed.
   *
   * @return the next event in the timeline, or null if there are no more events
   */
  public GameEvent getNextEvent() {
    if (indexOfLastAppliedEvent + 1 >= events.size()) {
      return null;
    }
    return events.get(indexOfLastAppliedEvent + 1).getEvent();
  }

  /**
   * Adds a new event to the end of the timeline.
   * @param timestamp the timestamp of the event. Must be greater than or equal to the
   *                  timestamp of the last event and the {@link #getCurrentTimestamp()).
   * @param event the event to add to the timeline
   * @throws IllegalArgumentException if the timestamp is invalid
   */
  public void addEvent(int timestamp, @NotNull GameEvent event) {
    if (timestamp < getLastEventTimestamp()) {
      throw new IllegalArgumentException("Can only add events to the end of the event list");
    }
    if (timestamp < getCurrentTimestamp()) {
      throw new IllegalArgumentException("Can't add events earlier than the current time");
    }
    events.add(new TimestampedGameEvent(timestamp, event));
  }

  /**
   * Jumps to a specific time on the timeline, updating the game model to reflect this. Any events
   * happening at exactly the specified timestamp will also be applied.
   *
   * <p>Exactly one non-incremental {@link GameEvent} will be applied.
   *
   * @param timestamp the timestamp to jump to
   */
  public void jumpTo(int timestamp) {
    if (timestamp < 0) {
      throw new IllegalArgumentException("Timestamp must be non-negative");
    }
    int ix = getLatestFullUpdate(timestamp);
    if (ix < 0) {
      // If there is no full update before the specified time stamp,
      // we can only assume that everything should be reset.
      model.reset();
      indexOfLastAppliedEvent = -1;
      currentTimestamp = 0;
    } else {
      TimestampedGameEvent event = events.get(ix);
      try {
        log.debug("Applying event " + event.getEvent());
        event.getEvent().apply(model);
      } catch (GameEventException e) {
        // This is bad; if a full replace fails there's not much we can do.
        // This shouldn't really happen.
        log.error(
            String.format(
                "Failed to apply full update event %s at %s: %s",
                event.getClass().getSimpleName(),
                formatMillis(event.getTimestamp()),
                e.getMessage()));
        return;
      }
      indexOfLastAppliedEvent = ix;
      currentTimestamp = events.get(ix).getTimestamp();
    }

    playTo(timestamp);
  }

  /**
   * Finds the latest non-incremental {@link GameEvent} that occurs at or before the specified
   * timestamp.
   *
   * @param timestamp the latest time to find a non-incremental GameEvent
   * @return the index of a non-incremental {@link GameEvent}, or -1 if none can be found in the
   *     timeline at or before the given timestamp
   */
  private int getLatestFullUpdate(int timestamp) {
    // Find the most recent full update and apply all actions that have happened since then
    int ix = Collections.binarySearch(events, new TimestampedGameEvent(timestamp, new NullEvent()));
    if (ix < 0) {
      int insertionPoint = -ix - 1;
      ix = insertionPoint - 1;
    } else {
      // There was (at least) one event with this timestamp
      // Find the last event with this timestamp
      while (ix + 1 < events.size() && events.get(ix + 1).getTimestamp() == timestamp) {
        ix++;
      }
    }
    // ix = index of last event with highest timestamp not greater than the specified one
    while (ix >= 0 && events.get(ix).getEvent().isIncremental()) {
      ix--;
    }
    return ix;
  }

  /**
   * Applies all events between {@link #getCurrentTimestamp()} (exclusive) and the specified
   * timestamp (inclusive). This has better performance than {@link #jumpTo(int)} in case of small
   * deltas since only incremental updates usually needs to be applied.
   *
   * @param timestamp the timestamp to play to. Must not be smaller than the {@link
   *     #getCurrentTimestamp()}
   * @return number of events that were successfully applied
   * @throws IllegalArgumentException if the target time is earlier than the current time
   */
  public int playTo(int timestamp) {
    if (timestamp < currentTimestamp) {
      throw new IllegalArgumentException(
          "Can't play to an earlier point in time than the current time");
    }
    // TODO: If the game model has been mutated outside the timeline, should that be detected?
    int numEventsApplied = 0;
    while (getNextEventTimestamp() <= timestamp) {
      TimestampedGameEvent event = events.get(++indexOfLastAppliedEvent);
      try {
        log.debug("Applying event " + event.getEvent());
        event.getEvent().apply(model);
        numEventsApplied++;
      } catch (GameEventException e) {
        log.warn(
            String.format(
                "Failed to apply event %s at %s: %s",
                event.getClass().getSimpleName(),
                formatMillis(event.getTimestamp()),
                e.getMessage()));
      }
    }
    currentTimestamp = timestamp;
    return numEventsApplied;
  }

  /**
   * Applies the next event in the timeline, if there are any, and updates the currentTime.
   *
   * @throws GameEventException if there was an error applying the event.
   */
  public void applyNextEvent() throws GameEventException {
    GameEvent event = getNextEvent();
    if (event == null) {
      return;
    }
    currentTimestamp = getNextEventTimestamp();
    indexOfLastAppliedEvent++;
    event.apply(model);
  }
}
