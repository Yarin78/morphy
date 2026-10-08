package se.yarin.morphy.positions;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * A position as a segment holds it: its hash, the side to move, and the games that reached it, by
 * the move they played from it.
 *
 * @param groups the moves, each with its games in id order; at least one game in all
 */
record PositionEntry(long hash, boolean whiteToMove, @NotNull List<MoveGroup> groups) {

  /** The games that reached the position. */
  int games() {
    int n = 0;
    for (MoveGroup g : groups) {
      n += g.gameIds().length;
    }
    return n;
  }
}
