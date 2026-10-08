package se.yarin.morphy.service.positions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
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
  private static final String AFTER_D4_NF6_C4 =
      "rnbqkb1r/pppppppp/5n2/8/2PP4/8/PP2PPPP/RNBQKBNR b KQkq - 0 2";

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

    // A copy, changed after its index is built
    Path copy = Files.createDirectories(dir.resolve("copy"));
    for (File f : file.getParentFile().listFiles(File::isFile)) {
      Files.copy(f.toPath(), copy.resolve(f.getName()));
    }

    databases = new DatabaseService();
    databases.registerDatabase("wch2", "World Championships", file.getPath());
    databases.getDatabaseConfig("wch2").setReadOnly(true);
    databases.registerDatabase("copy", "Copy", copy.resolve(file.getName()).toString());
    databases.getDatabaseConfig("copy").setReadOnly(true);

    // "all" and "old" (a filter) are built; "other" has another filter than its index was built
    // with; "unbuilt" isn't built; "copied" is built, then its database changes
    Path definitions = dir.resolve("position-indexes.json");
    Files.writeString(
        definitions,
        """
        {
          "all": {"name": "WCh", "database": "wch2", "path": "%1$s/all.positions"},
          "old": {"name": "WCh old", "database": "wch2", "filter": "date:..1960", "path": "%1$s/old.positions"},
          "other": {"name": "Other", "database": "wch2", "filter": "date:1961..", "path": "%1$s/old.positions"},
          "unbuilt": {"name": "Unbuilt", "database": "wch2", "path": "%1$s/unbuilt.positions"},
          "copied": {"name": "Copied", "database": "copy"}
        }
        """
            .formatted(dir.toString().replace("\\", "/")));
    positions = new PositionsService(databases, definitions.toString());
    assertEquals("missing", positions.info("all").status());
    positions.build("all").join();
    positions.build("old").join();
    positions.build("copied").join();
    Path copied = copy.resolve(file.getName());
    Files.setLastModifiedTime(
        copied, FileTime.fromMillis(Files.getLastModifiedTime(copied).toMillis() + 60_000));
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
        List.of("all", "old", "other", "unbuilt", "copied"),
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
    assertEquals("stale", byId.get("copied").status());
  }

  @Test
  void startPositionHasEveryGameAndItsMoves() {
    PositionSearchResponse response = positions.search("all", START, "+id", 0, 100, false);
    assertEquals("all", response.indexId());
    assertEquals("wch2", response.databaseId());
    assertEquals(PositionIndexState.READY, response.index());
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
  void gamesAreMostRelevantFirstByDefault() {
    int newest =
        positions.search("all", START, "-playedYear", 0, 1, false).games().games().getFirst().date().year();
    List<GameDto> games = positions.search("all", START, "", 0, 300, false).games().games();
    assertEquals("-relevance", positions.search("all", START, "", 0, 1, false).games().metadata().sortBy());
    long previous = Long.MAX_VALUE;
    for (GameDto game : games) {
      int white = game.whiteElo() == null ? 0 : game.whiteElo();
      int black = game.blackElo() == null ? 0 : game.blackElo();
      int rating = white > 0 && black > 0 ? (white + black) / 2 : Math.max(white, black);
      int year = game.date().year();
      long relevance = rating - (long) PositionsService.RELEVANCE_ELO_PER_YEAR * (year > 0 ? newest - year : 100);
      assertTrue(relevance <= previous, "game " + game.id() + " is more relevant than the one before");
      previous = relevance;
    }
    // Recent games of strong players first: the first is from the last decade or so
    assertTrue(games.getFirst().date().year() >= newest - 10, "first game of " + games.getFirst().date());
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
  void withoutAnIndexEveryGameIsPlayedThrough() {
    for (String fen : List.of(START, AFTER_D4_NF6_C4, lastOfGame1)) {
      PositionSearchResponse indexed = positions.search("all", fen, "-relevance", 0, 1000, false);
      PositionSearchResponse scanned = positions.search("unbuilt", fen, "-relevance", 0, 1000, false);
      assertEquals("missing", scanned.index().status());
      assertTrue(scanned.index().message().contains("hasn't been built"), scanned.index().message());
      assertEquals(indexed.summary(), scanned.summary());
      assertEquals(ids(indexed), ids(scanned));
      // A later page, as the scan is kept
      assertEquals(
          ids(positions.search("all", fen, "+playedDate", 3, 5, false)),
          ids(positions.search("unbuilt", fen, "+playedDate", 3, 5, false)));
    }
  }

  @Test
  void anIndexOfAnotherFilterIsNotUsed() {
    PositionSearchResponse response = positions.search("other", START, "+id", 0, 1000, false);
    assertEquals("missing", response.index().status());
    assertTrue(response.index().message().contains("another filter"), response.index().message());
    List<GameDto> games = response.games().games();
    assertTrue(games.size() > 10);
    assertTrue(games.size() < fromStart);
    for (GameDto game : games) {
      assertTrue(game.date().year() >= 1961, "game " + game.id() + " of " + game.date());
    }
  }

  @Test
  void anOutOfDateIndexIsUsed() {
    PositionSearchResponse response = positions.search("copied", START, "+id", 0, 100, false);
    assertEquals(new PositionIndexState("stale", null, 0L), response.index());
    assertEquals(fromStart, response.summary().games());
  }

  private static List<Long> ids(PositionSearchResponse response) {
    return response.games().games().stream().map(GameDto::id).toList();
  }

  @Test
  void invalidPositionsAreRefused() {
    assertThrows(
        IllegalArgumentException.class,
        () -> positions.search("all", "not a position", "+id", 0, 100, false));
  }
}
