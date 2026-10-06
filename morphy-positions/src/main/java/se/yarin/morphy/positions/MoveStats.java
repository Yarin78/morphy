package se.yarin.morphy.positions;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * How the games that played a move from a position went: worked out from the facts of the games
 * when it's asked for, or stored in the index for the moves played often.
 *
 * @param games the games
 * @param whiteWins the games White won, on the board or by forfeit
 * @param draws the games drawn
 * @param blackWins the games Black won
 * @param recentGames the games played since the index's {@code recentSince} year
 * @param lastYear the year of the latest game with a year, 0 if none has
 * @param eloSum the sum of the ratings of the side to move, in the games where it had one
 * @param eloCount the games where the side to move had a rating
 * @param topPlayers the highest rated players of the side to move, at most {@link #TOP_PLAYERS},
 *     the highest first
 */
public record MoveStats(
    int games,
    int whiteWins,
    int draws,
    int blackWins,
    int recentGames,
    int lastYear,
    long eloSum,
    int eloCount,
    @NotNull List<RatedPlayer> topPlayers) {

  /** The players kept of those who played a move. */
  public static final int TOP_PLAYERS = 5;

  /** The average rating of the side to move, 0 if none was rated. */
  public int averageElo() {
    return eloCount == 0 ? 0 : (int) Math.round((double) eloSum / eloCount);
  }

  /**
   * The statistics of some games.
   *
   * @param gameIds the games
   * @param whiteToMove whether White played the move, whose ratings and players count
   * @param recentSince the first year of the recent games
   */
  public static @NotNull MoveStats of(
      int @NotNull [] gameIds,
      @NotNull GameFactsTable facts,
      boolean whiteToMove,
      int recentSince) {
    int whiteWins = 0, draws = 0, blackWins = 0, recent = 0, lastYear = 0, eloCount = 0;
    long eloSum = 0;
    Map<Long, Integer> best = new HashMap<>();
    for (int id : gameIds) {
      switch (facts.result(id)) {
        case WHITE_WINS, WHITE_WINS_ON_FORFEIT -> whiteWins++;
        case DRAW, DRAW_ON_FORFEIT -> draws++;
        case BLACK_WINS, BLACK_WINS_ON_FORFEIT -> blackWins++;
        default -> {}
      }
      int year = facts.year(id);
      if (year > 0 && year >= recentSince) {
        recent++;
      }
      lastYear = Math.max(lastYear, year);
      int elo = whiteToMove ? facts.whiteElo(id) : facts.blackElo(id);
      if (elo > 0) {
        eloSum += elo;
        eloCount++;
        long player = whiteToMove ? facts.whitePlayer(id) : facts.blackPlayer(id);
        if (player >= 0) {
          best.merge(player, elo, Math::max);
        }
      }
    }
    List<RatedPlayer> top =
        best.entrySet().stream()
            .map(e -> new RatedPlayer(e.getKey(), e.getValue()))
            .sorted(
                Comparator.comparingInt(RatedPlayer::elo)
                    .reversed()
                    .thenComparingLong(RatedPlayer::playerId))
            .limit(TOP_PLAYERS)
            .toList();
    return new MoveStats(
        gameIds.length, whiteWins, draws, blackWins, recent, lastYear, eloSum, eloCount, top);
  }

  void write(@NotNull Bytes out) {
    out.putVar(games);
    out.putVar(whiteWins);
    out.putVar(draws);
    out.putVar(blackWins);
    out.putVar(recentGames);
    out.putVar(lastYear);
    out.putVar(eloSum);
    out.putVar(eloCount);
    out.putVar(topPlayers.size());
    for (RatedPlayer p : topPlayers) {
      out.putVar(p.playerId());
      out.putVar(p.elo());
    }
  }

  static @NotNull MoveStats read(@NotNull ByteBuffer buf) {
    int games = (int) Bytes.getVar(buf);
    int whiteWins = (int) Bytes.getVar(buf);
    int draws = (int) Bytes.getVar(buf);
    int blackWins = (int) Bytes.getVar(buf);
    int recent = (int) Bytes.getVar(buf);
    int lastYear = (int) Bytes.getVar(buf);
    long eloSum = Bytes.getVar(buf);
    int eloCount = (int) Bytes.getVar(buf);
    int n = (int) Bytes.getVar(buf);
    List<RatedPlayer> top = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      long player = Bytes.getVar(buf);
      top.add(new RatedPlayer(player, (int) Bytes.getVar(buf)));
    }
    return new MoveStats(games, whiteWins, draws, blackWins, recent, lastYear, eloSum, eloCount, top);
  }
}
