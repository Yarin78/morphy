package se.yarin.morphy.positions;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Move;

/**
 * The games of a database that reached a position, and the moves they played from it, as its
 * index has them; see {@link PositionIndex#find}.
 *
 * @param moves the moves played from the position, the most played first; the games that ended
 *     there are not among them
 * @param ended the games that ended in the position
 * @param gameIds all the games, in id order
 */
public record PositionGames(
    @NotNull List<PlayedMove> moves, @NotNull PlayedMove ended, int @NotNull [] gameIds) {

  /**
   * A move played from the position, or the games that ended there.
   *
   * @param move the move, null for the games that ended in the position
   * @param gameIds the games, in id order
   * @param stats their statistics
   */
  public record PlayedMove(@Nullable Move move, int @NotNull [] gameIds, @NotNull MoveStats stats) {}

  /** The games that reached the position, including those that ended there. */
  public int games() {
    return gameIds.length;
  }
}
