package se.yarin.morphy.positions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
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
              .build(scan, DatabaseIdentity.of(file.toPath(), db.gameCount()), filter, indexDir, dir.resolve("work"));
        }
        DatabaseIdentity identity = DatabaseIdentity.of(file.toPath(), db.gameCount());
        try (PositionIndex index = PositionIndex.open(indexDir)) {
          assertTrue(expected.size() > 5, filter + ": only " + expected.size() + " games");
          assertEquals(expected.size(), index.meta().games(), filter);
          assertEquals(filter, index.meta().filter());
          // Every game of the index starts in the start position here: the start position has them all
          Position start =
              PositionState.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1").position();
          Lookup.Shared atStart = assertInstanceOf(Lookup.Shared.class, index.lookup(start.getZobristHashLo()));
          Set<Integer> indexed = new HashSet<>();
          atStart.groups().forEach(g -> Arrays.stream(g.gameIds()).forEach(indexed::add));
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
      Path indexDir = dir.resolve(file.getName() + ".positions");
      try (GameScan scan = scanning.openScan()) {
        new PositionIndexBuilder(message -> {})
            .build(scan, DatabaseIdentity.of(file.toPath(), db.gameCount()), "", indexDir, dir.resolve("work"));
      }
      // Each position's games and the move each played from it, the first time
      Map<Long, Map<Integer, Integer>> expected = new HashMap<>();
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
            expected
                .computeIfAbsent(node.position().getZobristHashLo(), h -> new TreeMap<>())
                .putIfAbsent(id, move);
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
        Lookup.Shared atStart =
            assertInstanceOf(Lookup.Shared.class, index.lookup(start.getZobristHashLo()));
        assertEquals(
            fromStart, atStart.groups().stream().mapToInt(g -> g.gameIds().length).sum());
        long shared = 0;
        for (Map.Entry<Long, Map<Integer, Integer>> e : expected.entrySet()) {
          long hash = e.getKey();
          Map<Integer, Integer> found = new TreeMap<>();
          switch (index.lookup(hash)) {
            case Lookup.Shared s -> {
              shared++;
              for (MoveGroup g : s.groups()) {
                for (int id : g.gameIds()) {
                  found.put(id, g.moveCode());
                }
              }
            }
            case Lookup.SingleCandidates c -> {
              for (int id : c.gameIds()) {
                int move = PositionIndex.moveAfter(games.get(id), hash);
                if (move != PositionIndex.NOT_REACHED) {
                  found.put(id, move);
                }
              }
            }
          }
          assertEquals(e.getValue(), found, "position " + Long.toHexString(hash));
        }
        Set<Long> sharedHashes = new HashSet<>();
        expected.forEach((h, g) -> {
          if (g.size() > 1) sharedHashes.add(h);
        });
        assertEquals(sharedHashes.size(), shared);
        assertEquals(sharedHashes.size(), index.meta().sharedPositions());
        assertEquals(expected.size() - sharedHashes.size(), index.meta().singlePositions());

        // Scanning the games for a position, without the index, finds the same games and moves:
        // positions of some games, early (shared) and late (single-game)
        try (GameScan scan = scanning.openScan()) {
          List<Integer> ids = games.keySet().stream().sorted().toList();
          for (int i : List.of(0, 1, 100, 333, ids.size() / 2)) {
            GameMovesModel.Node node = games.get(ids.get(i)).root();
            for (int ply = 0; node.hasMoves(); ply++, node = node.mainNode()) {
              if (ply % 13 != 0) {
                continue;
              }
              Position position = node.position();
              PositionGames indexed = index.find(position, scan);
              PositionGames scanned = PositionScanner.find(position, scan);
              assertTrue(indexed.games() > 0);
              assertEquals(Arrays.toString(indexed.gameIds()), Arrays.toString(scanned.gameIds()));
              assertEquals(
                  indexed.moves().stream().map(m -> m.move().toSAN() + Arrays.toString(m.gameIds()) + m.stats()).toList(),
                  scanned.moves().stream().map(m -> m.move().toSAN() + Arrays.toString(m.gameIds()) + m.stats()).toList());
            }
          }
        }
      }
    }
  }
}
