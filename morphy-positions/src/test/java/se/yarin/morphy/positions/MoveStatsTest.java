package se.yarin.morphy.positions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import se.yarin.chess.Date;
import se.yarin.chess.GameResult;
import se.yarin.morphy.api.GameFacts;

class MoveStatsTest {

  @Test
  void topPlayersAreThoseWithTheHighestRatings() {
    Random random = new Random(3);
    for (int round = 0; round < 200; round++) {
      int games = 1 + random.nextInt(300);
      // Few players and ratings, so the same player and equal ratings come up often
      int players = 1 + random.nextInt(20);
      GameFactsTable facts = GameFactsTable.forGames(games);
      int[] ids = new int[games];
      for (int id = 1; id <= games; id++) {
        int elo = random.nextInt(4) == 0 ? 0 : 2400 + 10 * random.nextInt(10);
        facts.set(
            id,
            new GameFacts(
                GameResult.DRAW,
                new Date(2000, 0, 0),
                elo,
                2500,
                random.nextInt(players),
                -1));
        ids[id - 1] = id;
      }
      assertEquals(expected(ids, facts), MoveStats.of(ids, facts, true, 2000).topPlayers(), "round " + round);
    }
  }

  /** The top players worked out the plain way: each player's best rating, sorted. */
  private static List<RatedPlayer> expected(int[] ids, GameFactsTable facts) {
    Map<Long, Integer> best = new HashMap<>();
    for (int id : ids) {
      if (facts.whiteElo(id) > 0) {
        best.merge(facts.whitePlayer(id), facts.whiteElo(id), Math::max);
      }
    }
    return best.entrySet().stream()
        .map(e -> new RatedPlayer(e.getKey(), e.getValue()))
        .sorted(Comparator.comparingInt(RatedPlayer::elo).reversed().thenComparingLong(RatedPlayer::playerId))
        .limit(MoveStats.TOP_PLAYERS)
        .toList();
  }
}
