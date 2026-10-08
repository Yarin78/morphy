package se.yarin.morphy.positions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Move;
import se.yarin.chess.MoveCode;
import se.yarin.chess.Player;
import se.yarin.chess.Position;

/**
 * The games of a database that reached a position, and the moves they played from it: as its
 * index has them, see {@link PositionIndex#find}, or as a scan of its games finds them, see {@link
 * PositionScanner#find}.
 *
 * @param moves the moves played from the position, the most played first; the games that ended
 *     there are not among them
 * @param ended the games that ended in the position
 * @param gameIds all the games, in id order
 * @param facts the facts of the games, at least of these
 * @param recentSince the first year of the games counted as recent in the statistics: two years
 *     before the newest of the games looked through
 */
public record PositionGames(
    @NotNull List<PlayedMove> moves,
    @NotNull PlayedMove ended,
    int @NotNull [] gameIds,
    @NotNull GameFactsTable facts,
    int recentSince) {

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

  /**
   * The games of a position, from their groups by the move played.
   *
   * @param groups the groups; one whose stats aren't stored has them worked out from the facts
   */
  static @NotNull PositionGames of(
      @NotNull Position position,
      @NotNull List<MoveGroup> groups,
      @NotNull GameFactsTable facts,
      int recentSince) {
    boolean whiteToMove = position.playerToMove() == Player.WHITE;
    List<PlayedMove> moves = new ArrayList<>();
    PlayedMove ended = null;
    List<int[]> all = new ArrayList<>();
    // The most played first, then by move code, the same however the groups were found
    List<MoveGroup> ordered = new ArrayList<>(groups);
    ordered.sort(
        Comparator.comparingInt((MoveGroup g) -> -g.gameIds().length)
            .thenComparingInt(MoveGroup::moveCode));
    for (MoveGroup group : ordered) {
      MoveStats stats =
          group.stats() != null
              ? group.stats()
              : MoveStats.of(group.gameIds(), facts, whiteToMove, recentSince);
      all.add(group.gameIds());
      if (group.moveCode() == IndexFiles.GAME_ENDED) {
        ended = new PlayedMove(null, group.gameIds(), stats);
        continue;
      }
      Move move = MoveCode.move(position, group.moveCode());
      // A move that can't be played here would be a hash collision; never seen, but left out
      if (move != null) {
        moves.add(new PlayedMove(move, group.gameIds(), stats));
      }
    }
    if (ended == null) {
      ended = new PlayedMove(null, new int[0], MoveStats.of(new int[0], facts, whiteToMove, 0));
    }
    int[] gameIds = all.stream().flatMapToInt(Arrays::stream).sorted().toArray();
    return new PositionGames(List.copyOf(moves), ended, gameIds, facts, recentSince);
  }
}
