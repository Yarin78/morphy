package se.yarin.morphy.service.positions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.pgn.PositionState;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.Databases;
import se.yarin.morphy.api.GameScan;
import se.yarin.morphy.api.GameScanning;
import se.yarin.morphy.api.ScannedGame;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.positions.DatabaseIdentity;
import se.yarin.morphy.positions.PositionIndexBuilder;
import se.yarin.morphy.service.config.DatabaseConfig;
import se.yarin.morphy.service.databases.DatabaseService;

/** The position search of the sample v2 database, through an index built for it. */
class PositionsServiceTest {

  private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

  @TempDir static Path dir;

  private static DatabaseService databases;
  private static PositionsService positions;
  // The games not from a set-up position, which all start in the start position
  private static long fromStart;
  // Those of them without moves, which end in it
  private static long withoutMoves;
  // The last position of game 1, which only it reaches
  private static String lastOfGame1;

  @BeforeAll
  static void setUp() throws Exception {
    File file = new File("../test-databases/wch2/wch2.2cbh");
    assertTrue(file.exists(), "sample database not found: " + file.getAbsolutePath());
    Path index = dir.resolve("wch2.positions");
    try (Database db = Databases.open(file, AccessMode.READ_ONLY);
        GameScan scan = db.extension(GameScanning.class).orElseThrow().openScan()) {
      new PositionIndexBuilder(message -> {})
          .build(scan, DatabaseIdentity.of(file.toPath(), db.gameCount()), index, dir.resolve("work"));
      for (int id = 1; id <= scan.maxId(); id++) {
        ScannedGame game = scan.read(id);
        if (game != null && !game.moves().isSetupPosition()) {
          fromStart++;
          if (!game.moves().root().hasMoves()) {
            withoutMoves++;
          }
        }
      }
      GameMovesModel.Node node = scan.read(1).moves().root();
      while (node.hasMoves()) {
        node = node.mainNode();
      }
      lastOfGame1 = PositionState.toFen(node.position(), node.ply());
    }

    databases = new DatabaseService();
    databases.registerDatabase("wch2", "World Championships", file.getPath());
    DatabaseConfig config = databases.getDatabaseConfig("wch2");
    config.setReadOnly(true);
    config.setReferenceName("WCh");
    config.setPositionIndex(index.toString());

    databases.registerDatabase("plain", "Not a reference database", file.getPath());
    databases.getDatabaseConfig("plain").setReadOnly(true);

    databases.registerDatabase("unindexed", "Without an index", file.getPath());
    DatabaseConfig unindexed = databases.getDatabaseConfig("unindexed");
    unindexed.setReadOnly(true);
    unindexed.setReferenceName("None");
    unindexed.setPositionIndex(dir.resolve("missing.positions").toString());

    positions = new PositionsService(databases);
  }

  @AfterAll
  static void tearDown() {
    positions.closeAll();
    databases.cleanup();
  }

  @Test
  void startPositionHasEveryGameAndItsMoves() {
    PositionSearchResponse response = positions.search("wch2", START, "+id", 0, 100, false);
    PositionSummary summary = response.summary();
    assertNotNull(summary);
    assertEquals(START, summary.fen());
    assertEquals(fromStart, summary.games());
    assertEquals(fromStart, (long) response.games().totalCount());
    assertEquals(100, response.games().games().size());
    assertEquals(1L, response.games().games().getFirst().id());
    List<PositionMove> moves = summary.moves();
    assertTrue(moves.stream().anyMatch(m -> m.san().equals("e4")));
    for (int i = 1; i < moves.size(); i++) {
      assertTrue(moves.get(i - 1).games() >= moves.get(i).games());
    }
    assertEquals(fromStart - withoutMoves, moves.stream().mapToInt(PositionMove::games).sum());
  }

  @Test
  void laterPagesComeWithoutTheSummary() {
    PositionSearchResponse first = positions.search("wch2", START, "+id", 0, 100, false);
    PositionSearchResponse second = positions.search("wch2", START, "+id", 100, 100, false);
    assertNull(second.summary());
    assertTrue(
        second.games().games().getFirst().id() > first.games().games().getLast().id());
  }

  @Test
  void gamesAreSortedAsAsked() {
    List<GameDto> games = positions.search("wch2", START, "-whiteElo", 0, 200, false).games().games();
    for (int i = 1; i < games.size(); i++) {
      int previous = games.get(i - 1).whiteElo() == null ? 0 : games.get(i - 1).whiteElo();
      int current = games.get(i).whiteElo() == null ? 0 : games.get(i).whiteElo();
      assertTrue(previous >= current, "not by White's rating at " + i);
    }
  }

  @Test
  void aPositionOnlyOneGameReachedIsFound() {
    PositionSearchResponse response = positions.search("wch2", lastOfGame1, "+id", 0, 100, true);
    assertEquals(1, response.summary().games());
    assertEquals(List.of(), response.summary().moves());
    assertEquals(1L, response.games().games().getFirst().id());
    assertNotNull(response.games().games().getFirst().moves());
  }

  @Test
  void aPositionNoGameReachedHasNoGames() {
    PositionSearchResponse response =
        positions.search("wch2", "8/8/8/4k3/8/8/8/4K2R w K - 0 1", "+id", 0, 100, false);
    assertEquals(0, response.summary().games());
    assertEquals(0, response.games().games().size());
  }

  @Test
  void unsupportedSortOrdersAreRefused() {
    assertThrows(
        IllegalArgumentException.class,
        () -> positions.search("wch2", START, "+event", 0, 100, false));
  }

  @Test
  void onlyReferenceDatabasesCanBeSearched() {
    assertThrows(
        IllegalArgumentException.class,
        () -> positions.search("plain", START, "+id", 0, 100, false));
  }

  @Test
  void aMissingIndexIsReported() {
    PositionIndexUnavailableException e =
        assertThrows(
            PositionIndexUnavailableException.class,
            () -> positions.search("unindexed", START, "+id", 0, 100, false));
    assertTrue(e.getMessage().contains("morphy positions build"), e.getMessage());
  }

  @Test
  void invalidPositionsAreRefused() {
    assertThrows(
        IllegalArgumentException.class,
        () -> positions.search("wch2", "not a position", "+id", 0, 100, false));
  }
}
