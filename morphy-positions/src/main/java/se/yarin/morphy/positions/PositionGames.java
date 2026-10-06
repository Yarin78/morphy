package se.yarin.morphy.positions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Move;
import se.yarin.chess.Player;
import se.yarin.chess.Position;
import se.yarin.morphy.api.GameScan;
import se.yarin.morphy.api.ScannedGame;

/**
 * The games of a database that reached a position, and the moves they played from it, as its
 * index has them: the single game of a position only one game reached is checked by playing
 * through it.
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

  /**
   * Finds the games of a position.
   *
   * @param scan a scan of the indexed database, to check the game of a single-game position by
   */
  public static @NotNull PositionGames find(
      @NotNull PositionIndex index, @NotNull GameScan scan, @NotNull Position position) {
    long hash = PositionKeys.hash(position);
    boolean whiteToMove = position.playerToMove() == Player.WHITE;
    List<MoveGroup> groups =
        switch (index.lookup(hash)) {
          case Lookup.Shared shared -> shared.groups();
          case Lookup.SingleCandidates candidates -> check(scan, candidates.gameIds(), hash);
        };
    List<PlayedMove> moves = new ArrayList<>();
    PlayedMove ended = null;
    List<int[]> all = new ArrayList<>();
    for (MoveGroup group : groups) {
      MoveStats stats = index.stats(group, whiteToMove);
      all.add(group.gameIds());
      if (group.moveCode() == PositionKeys.GAME_ENDED) {
        ended = new PlayedMove(null, group.gameIds(), stats);
        continue;
      }
      Move move = PositionKeys.move(position, group.moveCode());
      // A move that can't be played here would be a hash collision; never seen, but left out
      if (move != null) {
        moves.add(new PlayedMove(move, group.gameIds(), stats));
      }
    }
    moves.sort(Comparator.comparingInt((PlayedMove m) -> -m.gameIds().length));
    if (ended == null) {
      ended = new PlayedMove(null, new int[0], MoveStats.of(new int[0], index.facts(), whiteToMove, 0));
    }
    int[] gameIds = all.stream().flatMapToInt(Arrays::stream).sorted().toArray();
    return new PositionGames(List.copyOf(moves), ended, gameIds);
  }

  /** The candidates that do reach the position, as one group by the move each played. */
  private static List<MoveGroup> check(GameScan scan, int[] candidates, long hash) {
    List<MoveGroup> groups = new ArrayList<>();
    for (int id : candidates) {
      ScannedGame game = id <= scan.maxId() ? scan.read(id) : null;
      if (game == null) {
        continue;
      }
      int move = PositionKeys.moveAfter(game.moves(), hash);
      if (move != PositionKeys.NOT_REACHED) {
        groups.add(new MoveGroup(move, new int[] {id}, null));
      }
    }
    return groups;
  }
}
