package se.yarin.morphy.positions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.yarin.chess.Chess;
import se.yarin.chess.Date;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.GameResult;
import se.yarin.chess.MoveCode;
import se.yarin.chess.Position;
import se.yarin.morphy.api.GameFacts;
import se.yarin.morphy.api.GameScan;
import se.yarin.morphy.api.ScannedGame;

class PositionIndexTest {

  @TempDir static Path dir;

  private static final DatabaseIdentity DATABASE = new DatabaseIdentity(1234, 5678, 70);

  // Games 1-5 are the interesting ones; 6 is no game (a text, say); 7-66 play the same line, so
  // its moves have their statistics stored
  private static final Map<Integer, ScannedGame> GAMES = new HashMap<>();

  private static PositionIndex index;

  @BeforeAll
  static void build() throws IOException {
    add(1, "d2d4 g8f6 c2c4 e7e6", GameResult.WHITE_WINS, 2020, 2700, 2600, 1, 2);
    add(2, "c2c4 g8f6 d2d4 e7e6", GameResult.DRAW, 2021, 2750, 2650, 3, 4);
    add(3, "d2d4 g8f6 c2c4 g7g6 b1c3", GameResult.BLACK_WINS, 2010, 2500, 0, 5, 6);
    // The position after 2.Nf3 comes back after 4.Nf3, and counts once, with 2...Nc6
    add(4, "e2e4 e7e5 g1f3 b8c6 f3g1 c6b8 g1f3", GameResult.NOT_FINISHED, 2000, 0, 0, -1, -1);
    // Ends in a position other games go on from
    add(5, "d2d4 g8f6", GameResult.DRAW, 2022, 2400, 2400, 7, 8);
    for (int id = 7; id <= 66; id++) {
      add(id, "e2e4 c7c5", id % 2 == 0 ? GameResult.WHITE_WINS : GameResult.BLACK_WINS, 1990 + id % 30, 2000 + id, 2100, 100 + id, 200 + id);
    }
    GameScan scan =
        new GameScan() {
          @Override
          public int maxId() {
            return 66;
          }

          @Override
          public @Nullable ScannedGame read(int id) {
            return GAMES.get(id);
          }

          @Override
          public void close() {}
        };
    Path indexDir = dir.resolve("test.positions");
    new PositionIndexBuilder(message -> {}).build(scan, DATABASE, "", indexDir, dir.resolve("work"));
    index = PositionIndex.open(indexDir);
  }

  private static void add(
      int id, String moves, GameResult result, int year, int whiteElo, int blackElo, long white, long black) {
    GameMovesModel model = new GameMovesModel();
    GameMovesModel.Node node = model.root();
    for (String move : moves.split(" ")) {
      node = node.addMove(Chess.strToSqi(move.substring(0, 2)), Chess.strToSqi(move.substring(2, 4)));
    }
    GAMES.put(
        id,
        new ScannedGame(
            id, model, new GameFacts(result, new Date(year, 0, 0), whiteElo, blackElo, white, black)));
  }

  private static Position after(String moves) {
    Position position = Position.start();
    if (!moves.isEmpty()) {
      for (String move : moves.split(" ")) {
        position =
            position.doMove(Chess.strToSqi(move.substring(0, 2)), Chess.strToSqi(move.substring(2, 4)));
      }
    }
    return position;
  }

  private static Map<String, List<Integer>> movesFrom(String moves) {
    Position position = after(moves);
    Lookup lookup = index.lookup(position.getZobristHashLo());
    Lookup.Shared shared = assertInstanceOf(Lookup.Shared.class, lookup);
    return shared.groups().stream()
        .collect(
            Collectors.toMap(
                g ->
                    g.moveCode() == IndexFiles.GAME_ENDED
                        ? "end"
                        : MoveCode.move(position, g.moveCode()).toSAN(),
                g -> Arrays.stream(g.gameIds()).boxed().toList()));
  }

  @Test
  void startPositionHasEveryGame() {
    Map<String, List<Integer>> moves = movesFrom("");
    assertEquals(List.of(1, 3, 5), moves.get("d4"));
    assertEquals(List.of(2), moves.get("c4"));
    assertEquals(61, moves.get("e4").size());
    assertEquals(3, moves.size());
  }

  @Test
  void transpositionThroughDoublePawnStepsMeets() {
    Map<String, List<Integer>> moves = movesFrom("d2d4 g8f6 c2c4");
    assertEquals(Map.of("e6", List.of(1, 2), "g6", List.of(3)), moves);
    assertEquals(moves, movesFrom("c2c4 g8f6 d2d4"));
  }

  @Test
  void gamesEndingInAPositionAreAGroupOfTheirOwn() {
    assertEquals(Map.of("c4", List.of(1, 3), "end", List.of(5)), movesFrom("d2d4 g8f6"));
  }

  @Test
  void aRepeatedPositionCountsOnceWithTheFirstMove() {
    assertEquals(Map.of("Nc6", List.of(4)), single("e2e4 e7e5 g1f3"));
  }

  /** The move of the single game that reaches a position, as the lookup finds and checks it. */
  private static Map<String, List<Integer>> single(String moves) {
    Position position = after(moves);
    long hash = position.getZobristHashLo();
    Lookup.SingleCandidates candidates =
        assertInstanceOf(Lookup.SingleCandidates.class, index.lookup(hash));
    Map<String, List<Integer>> found = new HashMap<>();
    for (int id : candidates.gameIds()) {
      int move = PositionIndex.moveAfter(GAMES.get(id).moves(), hash);
      if (move != PositionIndex.NOT_REACHED) {
        String san = move == IndexFiles.GAME_ENDED ? "end" : MoveCode.move(position, move).toSAN();
        found.put(san, List.of(id));
      }
    }
    return found;
  }

  @Test
  void singleGamePositionsAreFoundAndChecked() {
    assertEquals(Map.of("Nc3", List.of(3)), single("d2d4 g8f6 c2c4 g7g6"));
    assertEquals(Map.of("end", List.of(3)), single("d2d4 g8f6 c2c4 g7g6 b1c3"));
  }

  @Test
  void positionsNoGameReachedAreNotFound() {
    assertEquals(Map.of(), single("a2a4"));
    assertEquals(Map.of(), single("d2d4 g8f6 c2c4 e7e6 b1c3"));
  }

  @Test
  void statsOfFrequentMovesAreStoredAndTheSameAsWorkedOut() {
    Position position = after("e2e4");
    Lookup.Shared shared = assertInstanceOf(Lookup.Shared.class, index.lookup(position.getZobristHashLo()));
    MoveGroup c5 = shared.groups().stream().filter(g -> g.gameIds().length == 60).findFirst().orElseThrow();
    assertNotNull(c5.stats());
    MoveStats worked =
        MoveStats.of(c5.gameIds(), index.facts(), false, index.meta().recentSince());
    assertEquals(worked, c5.stats());
    assertEquals(30, c5.stats().whiteWins());
    assertEquals(30, c5.stats().blackWins());
    assertEquals(2100, c5.stats().averageElo());
    assertEquals(MoveStats.TOP_PLAYERS, c5.stats().topPlayers().size());
    // The newest year is 2022, so the recent games are those since 2020
    assertEquals(2020, index.meta().recentSince());
    assertEquals(2019, c5.stats().lastYear());
  }

  @Test
  void statsOfRareMovesAreWorkedOutFromTheFacts() {
    Position position = after("d2d4 g8f6 c2c4");
    Lookup.Shared shared = assertInstanceOf(Lookup.Shared.class, index.lookup(position.getZobristHashLo()));
    MoveGroup e6 = shared.groups().stream().filter(g -> g.gameIds().length == 2).findFirst().orElseThrow();
    assertNull(e6.stats());
    MoveStats stats = index.stats(e6, false);
    assertEquals(1, stats.whiteWins());
    assertEquals(1, stats.draws());
    assertEquals(2625, stats.averageElo());
    assertEquals(List.of(new RatedPlayer(4, 2650), new RatedPlayer(2, 2600)), stats.topPlayers());
    assertEquals(2, stats.recentGames());
    assertEquals(2021, stats.lastYear());
  }

  @Test
  void factsAreKept() {
    GameFactsTable facts = index.facts();
    assertTrue(facts.exists(1));
    assertFalse(facts.exists(6));
    assertEquals(GameResult.BLACK_WINS, facts.result(3));
    assertEquals(2500, facts.whiteElo(3));
    assertEquals(0, facts.blackElo(3));
    assertEquals(-1, facts.whitePlayer(4));
    assertEquals(2010, facts.year(3));
  }

  @Test
  void staleWhenTheDatabaseChanged() {
    assertFalse(index.isStale(DATABASE, ""));
    assertTrue(index.isStale(new DatabaseIdentity(1234, 5679, 70), ""));
    assertTrue(index.isStale(DATABASE, "date:2000.."));
    assertEquals(DATABASE, index.meta().database());
  }
}
