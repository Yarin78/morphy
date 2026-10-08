package se.yarin.morphy.positions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import se.yarin.chess.Player;
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
  private static GameScan scan;

  @BeforeAll
  static void build() throws IOException {
    add(GAMES, 1, "d2d4 g8f6 c2c4 e7e6", GameResult.WHITE_WINS, 2020, 2700, 2600, 1, 2);
    add(GAMES, 2, "c2c4 g8f6 d2d4 e7e6", GameResult.DRAW, 2021, 2750, 2650, 3, 4);
    add(GAMES, 3, "d2d4 g8f6 c2c4 g7g6 b1c3", GameResult.BLACK_WINS, 2010, 2500, 0, 5, 6);
    // The position after 2.Nf3 comes back after 4.Nf3, and counts once, with 2...Nc6
    add(GAMES, 4, "e2e4 e7e5 g1f3 b8c6 f3g1 c6b8 g1f3", GameResult.NOT_FINISHED, 2000, 0, 0, -1, -1);
    // Ends in a position other games go on from
    add(GAMES, 5, "d2d4 g8f6", GameResult.DRAW, 2022, 2400, 2400, 7, 8);
    for (int id = 7; id <= 66; id++) {
      add(GAMES, id, "e2e4 c7c5", id % 2 == 0 ? GameResult.WHITE_WINS : GameResult.BLACK_WINS, 1990 + id % 30, 2000 + id, 2100, 100 + id, 200 + id);
    }
    scan = scanOf(GAMES, 66);
    index = build("test", scan, new PositionIndexBuilder(message -> {}));
  }

  private static PositionIndex build(String name, GameScan scan, PositionIndexBuilder builder)
      throws IOException {
    Path indexDir = dir.resolve(name + ".positions");
    builder.build(scan, DATABASE, "", indexDir);
    return PositionIndex.open(indexDir);
  }

  /** A scan of some games, up to an id. */
  private static GameScan scanOf(Map<Integer, ScannedGame> games, int maxId) {
    return new GameScan() {
      @Override
      public int maxId() {
        return maxId;
      }

      @Override
      public @Nullable ScannedGame read(int id) {
        return id <= maxId ? games.get(id) : null;
      }

      @Override
      public void close() {}
    };
  }

  private static void add(
      Map<Integer, ScannedGame> games,
      int id,
      String moves,
      GameResult result,
      int year,
      int whiteElo,
      int blackElo,
      long white,
      long black) {
    GameMovesModel model = new GameMovesModel();
    GameMovesModel.Node node = model.root();
    for (String move : moves.split(" ")) {
      node = node.addMove(Chess.strToSqi(move.substring(0, 2)), Chess.strToSqi(move.substring(2, 4)));
    }
    games.put(
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

  private static List<MoveGroup> groups(PositionIndex index, Position position) {
    return index.groups(position.getZobristHashLo(), position.playerToMove() == Player.WHITE);
  }

  private static Map<String, List<Integer>> movesFrom(String moves) {
    Position position = after(moves);
    return groups(index, position).stream()
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
    assertEquals(Map.of("Nc6", List.of(4)), movesFrom("e2e4 e7e5 g1f3"));
  }

  @Test
  void singleGamePositionsAreFoundWithTheirMove() {
    assertEquals(Map.of("Nc3", List.of(3)), movesFrom("d2d4 g8f6 c2c4 g7g6"));
    assertEquals(Map.of("end", List.of(3)), movesFrom("d2d4 g8f6 c2c4 g7g6 b1c3"));
  }

  @Test
  void positionsNoGameReachedAreNotFound() {
    assertEquals(Map.of(), movesFrom("a2a4"));
    assertEquals(Map.of(), movesFrom("d2d4 g8f6 c2c4 e7e6 b1c3"));
  }

  @Test
  void statsOfFrequentMovesAreStoredAndTheSameAsWorkedOut() {
    MoveGroup c5 =
        groups(index, after("e2e4")).stream()
            .filter(g -> g.gameIds().length == 60)
            .findFirst()
            .orElseThrow();
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
    MoveGroup e6 =
        groups(index, after("d2d4 g8f6 c2c4")).stream()
            .filter(g -> g.gameIds().length == 2)
            .findFirst()
            .orElseThrow();
    assertNull(e6.stats());
    MoveStats stats = MoveStats.of(e6.gameIds(), index.facts(), false, index.meta().recentSince());
    assertEquals(1, stats.whiteWins());
    assertEquals(1, stats.draws());
    assertEquals(2625, stats.averageElo());
    assertEquals(List.of(new RatedPlayer(4, 2650), new RatedPlayer(2, 2600)), stats.topPlayers());
    assertEquals(2, stats.recentGames());
    assertEquals(2021, stats.lastYear());
  }

  @Test
  void statsAddUpToThoseOfAllTheGames() {
    int[] some = {7, 9, 10, 31, 44}, others = {8, 12, 40, 66};
    int[] all = {7, 8, 9, 10, 12, 31, 40, 44, 66};
    int since = index.meta().recentSince();
    assertEquals(
        MoveStats.of(all, index.facts(), true, since),
        MoveStats.of(some, index.facts(), true, since)
            .plus(MoveStats.of(others, index.facts(), true, since)));
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
    assertEquals(65, index.meta().games());
  }

  @Test
  void staleWhenTheDatabaseChanged() {
    assertFalse(index.isStale(DATABASE, ""));
    assertTrue(index.isStale(new DatabaseIdentity(1234, 5679, 70), ""));
    assertTrue(index.isStale(DATABASE, "date:2000.."));
    assertEquals(DATABASE, index.meta().database());
  }

  @Test
  void scanningFindsWhatTheIndexHas() {
    for (Position position : positionsOf(GAMES)) {
      assertEquals(describe(index.find(position)), describe(PositionScanner.find(position, scan)));
      assertEquals(index.meta().recentSince(), PositionScanner.find(position, scan).recentSince());
    }
  }

  @Test
  void anIndexBuiltInSmallBatchesIsTheSame() throws IOException {
    try (PositionIndex batched =
        build("batched", scan, new PositionIndexBuilder(message -> {}).batchRecords(7))) {
      assertEquals(1, batched.meta().segments().size());
      assertSame(index, batched, GAMES);
      assertEquals(index.meta().sharedPositions(), batched.meta().sharedPositions());
      assertEquals(index.meta().singlePositions(), batched.meta().singlePositions());
    }
  }

  @Test
  void anUpdateAddsTheGamesAddedSince() throws IOException {
    Path indexDir = dir.resolve("updated.positions");
    PositionIndexBuilder builder = new PositionIndexBuilder(message -> {});
    // Games 61-66 are few positions, not enough to compact the index
    builder.build(scanOf(GAMES, 60), DATABASE, "", indexDir);
    IndexMeta updated = builder.update(scan, DATABASE, indexDir);
    assertEquals(66, updated.lastGameId());
    assertEquals(65, updated.games());
    try (PositionIndex index2 = PositionIndex.open(indexDir)) {
      assertEquals(2, index2.meta().segments().size());
      assertSame(index, index2, GAMES);
    }
    builder.compact(indexDir);
    try (PositionIndex compacted = PositionIndex.open(indexDir)) {
      assertEquals(1, compacted.meta().segments().size());
      assertSame(index, compacted, GAMES);
    }
  }

  @Test
  void changedAndDeletedGamesAreIndexedAgain() throws IOException {
    Map<Integer, ScannedGame> changed = new HashMap<>(GAMES);
    // Game 3 is replaced by another, game 2 deleted, game 70 added
    add(changed, 3, "e2e4 e7e5 g1f3 g8f6", GameResult.DRAW, 2010, 2500, 0, 5, 6);
    changed.remove(2);
    add(changed, 70, "d2d4 d7d5", GameResult.WHITE_WINS, 2022, 2200, 2210, 9, 10);

    Path indexDir = dir.resolve("changed.positions");
    PositionIndexBuilder builder = new PositionIndexBuilder(message -> {});
    builder.build(scan, DATABASE, "", indexDir);
    builder.update(scanOf(changed, 70), DATABASE, indexDir, 2, 3);
    try (PositionIndex fresh = build("changed-fresh", scanOf(changed, 70), builder);
        PositionIndex updated = PositionIndex.open(indexDir)) {
      assertEquals(Map.of("e6", List.of(1)), movesOf(updated, "d2d4 g8f6 c2c4"));
      assertEquals(
          Map.of("Nf6", List.of(3), "Nc6", List.of(4)), movesOf(updated, "e2e4 e7e5 g1f3"));
      assertFalse(updated.facts().exists(2));
      assertEquals(fresh.meta().games(), updated.meta().games());
      Map<Integer, ScannedGame> both = new HashMap<>(GAMES);
      both.putAll(changed);
      assertSame(fresh, updated, both);
      builder.compact(indexDir);
    }
    try (PositionIndex fresh = PositionIndex.open(dir.resolve("changed-fresh.positions"));
        PositionIndex compacted = PositionIndex.open(indexDir)) {
      assertEquals(1, compacted.meta().segments().size());
      Map<Integer, ScannedGame> both = new HashMap<>(GAMES);
      both.putAll(changed);
      assertSame(fresh, compacted, both);
      assertEquals(fresh.meta().sharedPositions(), compacted.meta().sharedPositions());
      assertEquals(fresh.meta().singlePositions(), compacted.meta().singlePositions());
    }
  }

  private static Map<String, List<Integer>> movesOf(PositionIndex index, String moves) {
    Position position = after(moves);
    return groups(index, position).stream()
        .collect(
            Collectors.toMap(
                g ->
                    g.moveCode() == IndexFiles.GAME_ENDED
                        ? "end"
                        : MoveCode.move(position, g.moveCode()).toSAN(),
                g -> Arrays.stream(g.gameIds()).boxed().toList()));
  }

  /** Every position of the games' main lines. */
  private static Set<Position> positionsOf(Map<Integer, ScannedGame> games) {
    Set<Position> positions = new LinkedHashSet<>();
    positions.add(after("a2a4"));
    for (ScannedGame game : games.values()) {
      GameMovesModel.Node node = game.moves().root();
      positions.add(node.position());
      while (node.hasMoves()) {
        node = node.mainNode();
        positions.add(node.position());
      }
    }
    return positions;
  }

  /** Two indexes have the same games, moves and statistics for every position of some games. */
  private static void assertSame(
      PositionIndex expected, PositionIndex actual, Map<Integer, ScannedGame> games) {
    for (Position position : positionsOf(games)) {
      assertEquals(describe(expected.find(position)), describe(actual.find(position)));
    }
  }

  private static String describe(PositionGames games) {
    return games.moves().stream().map(PositionIndexTest::describe).toList()
        + " "
        + describe(games.ended())
        + " "
        + Arrays.toString(games.gameIds());
  }

  private static String describe(PositionGames.PlayedMove move) {
    return (move.move() == null ? "end" : move.move().toSAN())
        + " "
        + Arrays.toString(move.gameIds())
        + " "
        + move.stats();
  }
}
