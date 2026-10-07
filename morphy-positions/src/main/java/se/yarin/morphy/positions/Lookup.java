package se.yarin.morphy.positions;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/** What the index knows of a position. */
sealed interface Lookup {

  /** A position several games reached: the moves they played from it. */
  record Shared(@NotNull List<MoveGroup> groups) implements Lookup {}

  /**
   * A position at most one game reached, if any: the games that may have, by the part of the key
   * the index keeps. Each must be checked with {@link PositionIndex#moveAfter}.
   */
  record SingleCandidates(int @NotNull [] gameIds) implements Lookup {}
}
