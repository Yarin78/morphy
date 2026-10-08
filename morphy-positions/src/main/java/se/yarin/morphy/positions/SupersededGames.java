package se.yarin.morphy.positions;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Which segment's entries of a game count: a game changed or deleted after a segment was written
 * is listed by a later segment as superseded, and its entries in the segments before that one are
 * void. A changed game is indexed again in the segment that supersedes it.
 */
final class SupersededGames {
  // The last segment superseding each game id; null when no segment supersedes any
  private final byte @Nullable [] lastSuperseder;

  /**
   * @param supersedes each segment's superseded games, the oldest segment first
   * @param maxId the highest game id
   */
  SupersededGames(@NotNull List<int[]> supersedes, int maxId) {
    if (supersedes.size() > 127) {
      throw new IllegalArgumentException("Too many segments: " + supersedes.size());
    }
    byte[] last = null;
    for (int s = 0; s < supersedes.size(); s++) {
      for (int id : supersedes.get(s)) {
        if (last == null) {
          last = new byte[maxId + 1];
        }
        if (id >= 0 && id <= maxId) {
          last[id] = (byte) s;
        }
      }
    }
    this.lastSuperseder = last;
  }

  /** Whether no game is superseded, so every entry counts. */
  boolean none() {
    return lastSuperseder == null;
  }

  /** Whether a game's entries in a segment count: no later segment supersedes it. */
  boolean counts(int segment, int gameId) {
    return lastSuperseder == null
        || gameId >= lastSuperseder.length
        || lastSuperseder[gameId] <= segment;
  }
}
