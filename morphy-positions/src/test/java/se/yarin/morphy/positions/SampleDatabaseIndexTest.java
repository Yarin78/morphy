package se.yarin.morphy.positions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.MoveCode;
import se.yarin.chess.Player;
import se.yarin.chess.Position;
import se.yarin.chess.pgn.PositionState;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.Databases;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.api.GameScan;
import se.yarin.morphy.api.GameScanning;
import se.yarin.morphy.api.ScannedGame;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.api.query.Sort;
import se.yarin.morphy.model.GameDto;

/**
 * The index of each sample database, in both formats, has every position of every game's main
 * line, with the games that reached it and the moves they played, as found by going through the
 * games one by one.
 */
class SampleDatabaseIndexTest {

  @TempDir Path dir;

  @Test
  void v2DatabaseIndexMatchesItsGames() throws Exception {
    check(new File("../test-databases/wch2/wch2.2cbh"));
  }

  @Test
  void v1DatabaseIndexMatchesItsGames() throws Exception {
    check(new File("../test-databases/world-ch/World-ch.cbh"));
  }

  @Test
  void v2FilteredIndexHasTheMatchingGames() throws Exception {
    checkFiltered(new File("../test-databases/wch2/wch2.2cbh"));
  }

  @Test
  void v1FilteredIndexHasTheMatchingGames() throws Exception {
    checkFiltered(new File("../test-databases/world-ch/World-ch.cbh"));
  }

  /**
   * An index of the games matching a filter, one on the headers and one on an entity, has exactly
   * the games the database's search finds for it, and is out of date for another filter.
   */
  private void checkFiltered(File file) throws Exception {
    for (String filter : List.of("date:1950..1990", "white.name:Kasparov result:1-0")) {
      Path indexDir = dir.resolve(file.getName() + "-filtered.positions");
      Set<Integer> expected = new HashSet<>();
      try (Database db = Databases.open(file, AccessMode.READ_ONLY)) {
        for (GameDto game :
            db.findGames(Query.of(filter, Sort.natural(), 0, 100_000), GameFetchOptions.headersOnly())
                .items()) {
          expected.add(game.id().intValue());
        }
        try (GameScan scan = db.extension(GameScanning.class).orElseThrow().openScan(filter)) {
          new PositionIndexBuilder(message -> {})
              .build(scan, DatabaseIdentity.of(file.toPath(), db.gameCount()), filter, indexDir);
        }
        DatabaseIdentity identity = DatabaseIdentity.of(file.toPath(), db.gameCount());
        try (PositionIndex index = PositionIndex.open(indexDir)) {
          assertTrue(expected.size() > 5, filter + ": only " + expected.size() + " games");
          assertEquals(expected.size(), index.meta().games(), filter);
          assertEquals(filter, index.meta().filter());
          // Every game of the index starts in the start position here: the start position has them all
          Position start =
              PositionState.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1").position();
          Set<Integer> indexed = new HashSet<>();
          index
              .groups(start.getZobristHashLo(), true)
              .forEach(g -> Arrays.stream(g.gameIds()).forEach(indexed::add));
          assertEquals(expected, indexed, filter);
          assertFalse(index.isStale(identity, filter));
          assertTrue(index.isStale(identity, ""));
        }
      }
    }
  }

  @Test
  void anInvalidFilterIsRefused() throws Exception {
    for (File file :
        List.of(new File("../test-databases/wch2/wch2.2cbh"), new File("../test-databases/world-ch/World-ch.cbh"))) {
      try (Database db = Databases.open(file, AccessMode.READ_ONLY)) {
        GameScanning scanning = db.extension(GameScanning.class).orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> scanning.openScan("nosuchfield:1"));
      }
    }
  }

  private void check(File file) throws Exception {
    assertTrue(file.exists(), "sample database not found: " + file.getAbsolutePath());
    try (Database db = Databases.open(file, AccessMode.READ_ONLY)) {
      GameScanning scanning = db.extension(GameScanning.class).orElseThrow();
      DatabaseIdentity identity = DatabaseIdentity.of(file.toPath(), db.gameCount());
      Path indexDir = dir.resolve(file.getName() + ".positions");
      try (GameScan scan = scanning.openScan()) {
        new PositionIndexBuilder(message -> {}).build(scan, identity, "", indexDir);
      }
      // Each position's games and the move each played from it, the first time, and whether
      // White is to move in it
      Map<Long, Map<Integer, Integer>> expected = new HashMap<>();
      Map<Long, Boolean> whiteToMove = new HashMap<>();
      Map<Integer, GameMovesModel> games = new HashMap<>();
      try (GameScan scan = scanning.openScan()) {
        for (int id = 1; id <= scan.maxId(); id++) {
          ScannedGame game = scan.read(id);
          if (game == null) {
            continue;
          }
          games.put(id, game.moves());
          GameMovesModel.Node node = game.moves().root();
          while (true) {
            GameMovesModel.Node next = node.hasMoves() ? node.mainNode() : null;
            int move = next == null ? IndexFiles.GAME_ENDED : MoveCode.of(next.lastMove());
            long hash = node.position().getZobristHashLo();
            expected.computeIfAbsent(hash, h -> new TreeMap<>()).putIfAbsent(id, move);
            whiteToMove.put(hash, node.position().playerToMove() == Player.WHITE);
            if (next == null) {
              break;
            }
            node = next;
          }
        }
      }
      assertTrue(games.size() > 500, "only " + games.size() + " games");

      // Every game not from a set-up position starts in the start position: looked up by a
      // position of its own, as the games' roots share one whose hash may have been worked out
      // wrongly by several threads at once
      long fromStart = games.values().stream().filter(m -> !m.isSetupPosition()).count();
      Position start =
          PositionState.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
              .position();

      try (PositionIndex index = PositionIndex.open(indexDir)) {
        assertEquals(
            fromStart,
            index.groups(start.getZobristHashLo(), true).stream()
                .mapToInt(g -> g.gameIds().length)
                .sum());
        for (Map.Entry<Long, Map<Integer, Integer>> e : expected.entrySet()) {
          long hash = e.getKey();
          Map<Integer, Integer> found = new TreeMap<>();
          for (MoveGroup g : index.groups(hash, whiteToMove.get(hash))) {
            for (int id : g.gameIds()) {
              found.put(id, g.moveCode());
            }
          }
          assertEquals(e.getValue(), found, "position " + Long.toHexString(hash));
        }
        long shared = expected.values().stream().filter(g -> g.size() > 1).count();
        assertEquals(shared, index.meta().sharedPositions());
        assertEquals(expected.size() - shared, index.meta().singlePositions());

        // Scanning the games for a position, without the index, finds the same games and moves:
        // positions of some games, early (shared) and late (single-game)
        List<Position> positions = new ArrayList<>();
        List<Integer> ids = games.keySet().stream().sorted().toList();
        for (int i : List.of(0, 1, 100, 333, ids.size() / 2)) {
          GameMovesModel.Node node = games.get(ids.get(i)).root();
          for (int ply = 0; node.hasMoves(); ply++, node = node.mainNode()) {
            if (ply % 13 == 0) {
              positions.add(node.position());
            }
          }
        }
        try (GameScan scan = scanning.openScan()) {
          for (Position position : positions) {
            PositionGames indexed = index.find(position);
            assertTrue(indexed.games() > 0);
            assertEquals(describe(indexed), describe(PositionScanner.find(position, scan)));
          }
        }

        // Built in small batches, merged, the index is the same; and so it is built of the first
        // half of the games, then updated with the rest
        Path batched = dir.resolve(file.getName() + "-batched.positions");
        try (GameScan scan = scanning.openScan()) {
          new PositionIndexBuilder(message -> {})
              .batchRecords(5_000)
              .build(scan, identity, "", batched);
        }
        Path updated = dir.resolve(file.getName() + "-updated.positions");
        try (GameScan scan = scanning.openScan()) {
          PositionIndexBuilder builder = new PositionIndexBuilder(message -> {});
          builder.build(firstHalf(scan), identity, "", updated);
          builder.update(scan, identity, updated);
        }
        for (Path other : List.of(batched, updated)) {
          try (PositionIndex index2 = PositionIndex.open(other)) {
            assertEquals(index.meta().games(), index2.meta().games(), other.toString());
            for (Map.Entry<Long, Map<Integer, Integer>> e : expected.entrySet()) {
              long hash = e.getKey();
              boolean white = whiteToMove.get(hash);
              assertEquals(
                  describe(index.groups(hash, white)),
                  describe(index2.groups(hash, white)),
                  other + ": position " + Long.toHexString(hash));
            }
            // With the statistics, which an index of several segments adds up
            for (Position position : positions) {
              assertEquals(describe(index.find(position)), describe(index2.find(position)));
            }
          }
        }
      }
    }
  }

  /** A scan of the first half of a scan's games. */
  private static GameScan firstHalf(GameScan scan) {
    int half = scan.maxId() / 2;
    return new GameScan() {
      @Override
      public int maxId() {
        return half;
      }

      @Override
      public ScannedGame read(int id) {
        return id <= half ? scan.read(id) : null;
      }

      @Override
      public void close() {}
    };
  }

  private static String describe(PositionGames games) {
    return games.moves().stream()
            .map(m -> m.move().toSAN() + Arrays.toString(m.gameIds()) + m.stats())
            .toList()
        + Arrays.toString(games.ended().gameIds());
  }

  private static String describe(List<MoveGroup> groups) {
    return groups.stream()
        .map(g -> g.moveCode() + Arrays.toString(g.gameIds()))
        .toList()
        .toString();
  }
}
