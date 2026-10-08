package se.yarin.morphy.positions;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
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
    TopPlayers top = new TopPlayers();
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
          top.add(player, elo);
        }
      }
    }
    return new MoveStats(
        gameIds.length, whiteWins, draws, blackWins, recent, lastYear, eloSum, eloCount, top.list());
  }

  /**
   * The statistics of these games and others, none of them among both: the counts added up, the
   * latest year and the highest rated players of the two.
   */
  public @NotNull MoveStats plus(@NotNull MoveStats other) {
    TopPlayers top = new TopPlayers();
    for (RatedPlayer p : topPlayers) {
      top.add(p.playerId(), p.elo());
    }
    for (RatedPlayer p : other.topPlayers) {
      top.add(p.playerId(), p.elo());
    }
    return new MoveStats(
        games + other.games,
        whiteWins + other.whiteWins,
        draws + other.draws,
        blackWins + other.blackWins,
        recentGames + other.recentGames,
        Math.max(lastYear, other.lastYear),
        eloSum + other.eloSum,
        eloCount + other.eloCount,
        top.list());
  }

  /**
   * The players with the highest ratings, each with their highest, as games are gone through: the
   * best few kept in small arrays, the highest first, the lower player id first among equals. A
   * game rarely gets in, so most cost a comparison.
   */
  private static final class TopPlayers {
    private final long[] players = new long[TOP_PLAYERS];
    private final int[] elos = new int[TOP_PLAYERS];
    private int size;

    private boolean better(int elo, long player, int i) {
      return elo > elos[i] || (elo == elos[i] && player < players[i]);
    }

    void add(long player, int elo) {
      if (size == TOP_PLAYERS && !better(elo, player, size - 1)) {
        // Not among the best; if the player is, they're there with a rating at least this high
        return;
      }
      int at = -1;
      for (int i = 0; i < size; i++) {
        if (players[i] == player) {
          at = i;
          break;
        }
      }
      if (at >= 0) {
        if (elo <= elos[at]) {
          return;
        }
      } else {
        at = size < TOP_PLAYERS ? size++ : size - 1;
      }
      // In at's place, moved up past those it's better than
      while (at > 0 && better(elo, player, at - 1)) {
        players[at] = players[at - 1];
        elos[at] = elos[at - 1];
        at--;
      }
      players[at] = player;
      elos[at] = elo;
    }

    List<RatedPlayer> list() {
      List<RatedPlayer> list = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
        list.add(new RatedPlayer(players[i], elos[i]));
      }
      return list;
    }
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
