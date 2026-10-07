package se.yarin.morphy.service.positions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
import se.yarin.morphy.service.databases.DatabaseService;

/**
 * Position indexes of the sample v2 database, defined in a file as the service reads them: built
 * through the service, listed, and searched.
 */
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
    try (Database db = Databases.open(file, AccessMode.READ_ONLY);
        GameScan scan = db.extension(GameScanning.class).orElseThrow().openScan()) {
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
    databases.getDatabaseConfig("wch2").setReadOnly(true);

    // "all" and "old" (a filter) are built; "other" has another filter than its index was built
    // with; "unbuilt" isn't built
    Path definitions = dir.resolve("position-indexes.json");
    Files.writeString(
        definitions,
        """
        {
          "all": {"name": "WCh", "database": "wch2", "path": "%1$s/all.positions"},
          "old": {"name": "WCh old", "database": "wch2", "filter": "date:..1960", "path": "%1$s/old.positions"},
          "other": {"name": "Other", "database": "wch2", "filter": "date:1961..", "path": "%1$s/old.positions"},
          "unbuilt": {"name": "Unbuilt", "database": "wch2", "path": "%1$s/unbuilt.positions"}
        }
        """
            .formatted(dir.toString().replace("\\", "/")));
    positions = new PositionsService(databases, definitions.toString());
    assertEquals("missing", positions.info("all").status());
    positions.build("all").join();
    positions.build("old").join();
  }

  @AfterAll
  static void tearDown() {
    positions.closeAll();
    databases.cleanup();
  }

  @Test
  void indexesAreListedWithTheirStatus() {
    Map<String, PositionIndexInfo> byId =
        positions.list().stream().collect(Collectors.toMap(PositionIndexInfo::id, i -> i));
    assertEquals(
        List.of("all", "old", "other", "unbuilt"),
        positions.list().stream().map(PositionIndexInfo::id).toList());
    assertEquals("ready", byId.get("all").status());
    assertEquals(fromStart, (long) byId.get("all").games());
    assertEquals("ready", byId.get("old").status());
    assertEquals("date:..1960", byId.get("old").filter());
    assertTrue(byId.get("old").games() < byId.get("all").games());
    assertEquals("stale", byId.get("other").status());
    assertEquals("missing", byId.get("unbuilt").status());
    assertNull(byId.get("unbuilt").games());
    assertEquals("wch2", byId.get("all").databaseId());
  }

  @Test
  void startPositionHasEveryGameAndItsMoves() {
    PositionSearchResponse response = positions.search("all", START, "+id", 0, 100, false);
    assertEquals("all", response.indexId());
    assertEquals("wch2", response.databaseId());
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
  void aFilteredIndexHasOnlyItsGames() {
    PositionSearchResponse response = positions.search("old", START, "-playedDate", 0, 1000, false);
    List<GameDto> games = response.games().games();
    assertTrue(games.size() > 10);
    assertTrue(games.size() < fromStart);
    for (GameDto game : games) {
      assertTrue(game.date().year() <= 1960, "game " + game.id() + " of " + game.date());
    }
    assertEquals(games.size(), response.summary().games());
  }

  @Test
  void laterPagesComeWithoutTheSummary() {
    PositionSearchResponse first = positions.search("all", START, "+id", 0, 100, false);
    PositionSearchResponse second = positions.search("all", START, "+id", 100, 100, false);
    assertNull(second.summary());
    assertTrue(second.games().games().getFirst().id() > first.games().games().getLast().id());
  }

  @Test
  void gamesAreSortedAsAsked() {
    List<GameDto> games = positions.search("all", START, "-whiteElo", 0, 200, false).games().games();
    for (int i = 1; i < games.size(); i++) {
      int previous = games.get(i - 1).whiteElo() == null ? 0 : games.get(i - 1).whiteElo();
      int current = games.get(i).whiteElo() == null ? 0 : games.get(i).whiteElo();
      assertTrue(previous >= current, "not by White's rating at " + i);
    }
  }

  @Test
  void aPositionOnlyOneGameReachedIsFound() {
    PositionSearchResponse response = positions.search("all", lastOfGame1, "+id", 0, 100, true);
    assertEquals(1, response.summary().games());
    assertEquals(List.of(), response.summary().moves());
    assertEquals(1L, response.games().games().getFirst().id());
    assertNotNull(response.games().games().getFirst().moves());
  }

  @Test
  void aPositionNoGameReachedHasNoGames() {
    PositionSearchResponse response =
        positions.search("all", "8/8/8/4k3/8/8/8/4K2R w K - 0 1", "+id", 0, 100, false);
    assertEquals(0, response.summary().games());
    assertEquals(0, response.games().games().size());
  }

  @Test
  void unsupportedSortOrdersAreRefused() {
    assertThrows(
        IllegalArgumentException.class,
        () -> positions.search("all", START, "+event", 0, 100, false));
  }

  @Test
  void unknownIndexesAreRefused() {
    assertThrows(
        IllegalArgumentException.class,
        () -> positions.search("nosuch", START, "+id", 0, 100, false));
  }

  @Test
  void aMissingOrOutOfDateIndexIsReported() {
    PositionIndexUnavailableException missing =
        assertThrows(
            PositionIndexUnavailableException.class,
            () -> positions.search("unbuilt", START, "+id", 0, 100, false));
    assertTrue(missing.getMessage().contains("morphy positions build"), missing.getMessage());
    PositionIndexUnavailableException stale =
        assertThrows(
            PositionIndexUnavailableException.class,
            () -> positions.search("other", START, "+id", 0, 100, false));
    assertTrue(stale.getMessage().contains("out of date"), stale.getMessage());
    assertTrue(stale.getMessage().contains("--filter \"date:1961..\""), stale.getMessage());
  }

  @Test
  void invalidPositionsAreRefused() {
    assertThrows(
        IllegalArgumentException.class,
        () -> positions.search("all", "not a position", "+id", 0, 100, false));
  }
}
