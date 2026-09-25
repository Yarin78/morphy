package se.yarin.morphy;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.Test;
import se.yarin.chess.Date;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.DatabaseFormat;
import se.yarin.morphy.api.Databases;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.api.query.ResultPage;
import se.yarin.morphy.api.query.SearchSchema;
import se.yarin.morphy.api.query.Sort;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.model.PlayerDto;

/** The v1 database through the vendor-neutral {@link Database} facade. */
public class DatabaseCbhTest {

  /** A fresh, writable copy of the World-ch database. */
  private File worldChCbh() throws IOException {
    File dir = ResourceLoader.materializeStreamPath(DatabaseCbh.class, "database/World-ch");
    return new File(dir, "World-ch.cbh");
  }

  private static final GameFetchOptions HEADERS = GameFetchOptions.headersOnly();

  // ── Opening, capabilities ──────────────────────────────────────────────────

  @Test
  public void opensThroughFactoryAsCbh() throws IOException {
    try (Database db = Databases.open(worldChCbh(), AccessMode.READ_WRITE)) {
      assertTrue(db instanceof DatabaseCbhFacade);
      assertEquals(DatabaseFormat.CBH, db.format());
      assertTrue(db.capabilities().canWrite());
      assertTrue(db.capabilities().hasEntities());
      assertTrue(db.gameCount() >= 73);
    }
  }

  @Test
  public void readOnlyDatabaseRejectsWrites() throws IOException {
    try (Database db = Databases.open(worldChCbh(), AccessMode.READ_ONLY)) {
      assertFalse(db.capabilities().canWrite());
      assertFalse(db.capabilities().canEditEntities());
      GameDto game = db.getGame(1, HEADERS);
      assertThrows(UnsupportedOperationException.class, () -> db.addGame(game));
    }
  }

  // ── Games ─────────────────────────────────────────────────────────────────

  @Test
  public void readsAGameAsDto() throws IOException {
    try (Database db = Databases.open(worldChCbh(), AccessMode.READ_ONLY)) {
      GameDto game = db.getGame(73, GameFetchOptions.full());
      assertNotNull(game);
      assertEquals("game", game.type());
      assertEquals("Chigorin", game.whitePlayer().lastName());
      assertEquals("Steinitz", game.blackPlayer().lastName());
      assertEquals("World-ch04 Steinitz-Chigorin +10-8=5", game.tournament().title());
      assertNotNull(game.moves().pgn());
    }
  }

  @Test
  public void missingGameIsNull() throws IOException {
    try (Database db = Databases.open(worldChCbh(), AccessMode.READ_ONLY)) {
      assertNull(db.getGame(100000, HEADERS));
      assertNull(db.getGame(0, HEADERS));
    }
  }

  @Test
  public void listsAllGamesInIdOrder() throws IOException {
    try (Database db = Databases.open(worldChCbh(), AccessMode.READ_ONLY)) {
      ResultPage<GameDto> page = db.findGames(Query.all(10, 5), HEADERS);
      assertEquals(Long.valueOf(db.gameCount()), page.total());
      assertEquals(List.of(11L, 12L, 13L, 14L, 15L), ids(page));

      ResultPage<GameDto> last =
          db.findGames(Query.of(null, Sort.parse("-id"), 0, 2), HEADERS);
      assertEquals(List.of(db.gameCount(), db.gameCount() - 1), ids(last));

      ResultPage<GameDto> beyond = db.findGames(Query.all((int) db.gameCount(), 5), HEADERS);
      assertTrue(beyond.items().isEmpty());
    }
  }

  @Test
  public void findsGamesByFilter() throws IOException {
    try (Database db = Databases.open(worldChCbh(), AccessMode.READ_ONLY)) {
      ResultPage<GameDto> page = db.findGames(Query.of("Chigorin", Sort.natural(), 0, 1000), HEADERS);
      assertTrue(page.total() > 0);
      assertEquals(page.total().intValue(), page.items().size());
      for (GameDto game : page.items()) {
        assertTrue(
            "Chigorin".equals(game.whitePlayer().lastName())
                || "Chigorin".equals(game.blackPlayer().lastName()));
      }
      assertTrue(ids(page).contains(73L));
      assertEquals("player.name:Chigorin", page.appliedFilter());
    }
  }

  @Test
  public void pagesOfAFilteredResultFitTogether() throws IOException {
    try (Database db = Databases.open(worldChCbh(), AccessMode.READ_ONLY)) {
      Sort byDate = Sort.parse("+date");
      List<Long> all = ids(db.findGames(Query.of("Steinitz", byDate, 0, 10), HEADERS));
      List<Long> first = ids(db.findGames(Query.of("Steinitz", byDate, 0, 5), HEADERS));
      List<Long> second = ids(db.findGames(Query.of("Steinitz", byDate, 5, 5), HEADERS));
      assertEquals(10, all.size());
      assertEquals(all.subList(0, 5), first);
      assertEquals(all.subList(5, 10), second);
    }
  }

  @Test
  public void sortsGames() throws IOException {
    try (Database db = Databases.open(worldChCbh(), AccessMode.READ_ONLY)) {
      List<GameDto> games =
          db.findGames(Query.of("Steinitz", Sort.parse("-date"), 0, 1000), HEADERS).items();
      for (int i = 1; i < games.size(); i++) {
        Date previous = games.get(i - 1).date();
        Date current = games.get(i).date();
        assertTrue(previous + " before " + current, previous.compareTo(current) >= 0);
      }
    }
  }

  @Test
  public void unknownSortFieldIsRejected() throws IOException {
    try (Database db = Databases.open(worldChCbh(), AccessMode.READ_ONLY)) {
      assertThrows(
          IllegalArgumentException.class,
          () -> db.findGames(Query.of(null, Sort.parse("colour"), 0, 10), HEADERS));
    }
  }

  @Test
  public void describesGameSearch() throws IOException {
    try (Database db = Databases.open(worldChCbh(), AccessMode.READ_ONLY)) {
      SearchSchema schema = db.gameSearchSchema();
      assertEquals("player.name", schema.defaultField());
      assertTrue(schema.fields().contains("result"));
      assertTrue(schema.hiddenFields().contains("playerid"));
      assertTrue(schema.sortFields().stream().anyMatch(f -> f.name().equals("id")));
    }
  }

  @Test
  public void explainsAGameSearch() throws IOException {
    try (Database db = Databases.open(worldChCbh(), AccessMode.READ_ONLY)) {
      Query query = Query.of("Steinitz", Sort.natural(), 0, 10);
      CbhDiagnostics diagnostics = db.extension(CbhDiagnostics.class).orElseThrow();
      CbhDiagnostics.QueryExplanation explanation = diagnostics.explainGames(query, true);
      assertFalse(explanation.plans().isEmpty());
      CbhDiagnostics.ExplainedPlan best = explanation.plans().getFirst();
      assertTrue(best.executed());
      assertEquals(db.findGames(query, HEADERS).total().intValue(), best.resultCount().intValue());
      assertEquals(Boolean.TRUE, explanation.allPlansAgree());
    }
  }

  @Test
  public void rawRecordsMatchTheStorage() throws IOException {
    DatabaseCbh engine = DatabaseCbh.open(worldChCbh(), DatabaseMode.READ_ONLY);
    try (DatabaseCbhFacade db = new DatabaseCbhFacade(engine)) {
      // Game 1 is annotated, game 73 is not
      List<CbhDiagnostics.RawRecord> game = db.rawGame(1);
      assertEquals(List.of(".cbh", ".cbj", ".cbg", ".cba"), files(game));
      assertArrayEquals(bytes(engine.gameHeaderIndex().getRaw(1)), game.get(0).bytes());
      assertArrayEquals(bytes(engine.extendedGameHeaderStorage().getRaw(1)), game.get(1).bytes());
      assertArrayEquals(bytes(engine.getGame(1).getMovesBlob()), game.get(2).bytes());
      assertArrayEquals(bytes(engine.getGame(1).getAnnotationsBlob()), game.get(3).bytes());
      assertEquals(List.of(".cbh", ".cbj", ".cbg"), files(db.rawGame(73)));

      List<CbhDiagnostics.RawRecord> player = db.rawEntity(EntityKind.PLAYER, 1);
      assertEquals(List.of(".cbp"), files(player));
      assertArrayEquals(engine.playerIndex().getRaw(1), player.get(0).bytes());

      List<CbhDiagnostics.RawRecord> tournament = db.rawEntity(EntityKind.TOURNAMENT, 0);
      assertEquals(List.of(".cbt", ".cbtt"), files(tournament));

      assertThrows(IllegalArgumentException.class, () -> db.rawGame(100_000));
    }
  }

  private static List<String> files(List<CbhDiagnostics.RawRecord> records) {
    return records.stream().map(CbhDiagnostics.RawRecord::file).toList();
  }

  private static byte[] bytes(ByteBuffer buffer) {
    ByteBuffer copy = buffer.duplicate();
    byte[] bytes = new byte[copy.remaining()];
    copy.get(bytes);
    return bytes;
  }

  // ── Entities ──────────────────────────────────────────────────────────────

  @Test
  public void findsAndGetsEntities() throws IOException {
    try (Database db = Databases.open(worldChCbh(), AccessMode.READ_ONLY)) {
      assertTrue(db.entityCount(EntityKind.PLAYER) > 0);

      ResultPage<PlayerDto> page =
          db.findEntities(EntityKind.PLAYER, Query.of("Steinitz", Sort.natural(), 0, 10));
      assertEquals(1, page.items().size());
      PlayerDto steinitz = page.items().getFirst();
      assertEquals("Steinitz", steinitz.lastName());
      assertTrue(steinitz.gameCount() > 0);

      assertEquals(steinitz, db.getEntity(EntityKind.PLAYER, steinitz.id()));
      assertNull(db.getEntity(EntityKind.PLAYER, 1_000_000));

      ResultPage<PlayerDto> all =
          db.findEntities(EntityKind.PLAYER, Query.all(0, 10_000));
      assertEquals(Long.valueOf(all.items().size()), all.total());
      assertTrue(all.items().stream().allMatch(p -> p.gameCount() != null && p.gameCount() > 0));
    }
  }

  @Test
  public void updatesAnEntity() throws IOException {
    try (Database db = Databases.open(worldChCbh(), AccessMode.READ_WRITE)) {
      PlayerDto steinitz = findPlayer(db, "Steinitz");
      PlayerDto renamed =
          new PlayerDto(steinitz.id(), "Steinitz", "Wilhelm", null, null, null);

      PlayerDto updated = db.updateEntity(EntityKind.PLAYER, steinitz.id(), renamed);
      assertEquals("Wilhelm", updated.firstName());
      assertEquals(steinitz.gameCount(), updated.gameCount());
      assertEquals("Wilhelm", db.getEntity(EntityKind.PLAYER, steinitz.id()).firstName());
    }
  }

  @Test
  public void updateCantDuplicateAnotherEntity() throws IOException {
    try (Database db = Databases.open(worldChCbh(), AccessMode.READ_WRITE)) {
      PlayerDto steinitz = findPlayer(db, "Steinitz");
      PlayerDto chigorin = findPlayer(db, "Chigorin");
      PlayerDto clash =
          new PlayerDto(
              steinitz.id(), chigorin.lastName(), chigorin.firstName(), null, null, null);
      assertThrows(
          IllegalArgumentException.class,
          () -> db.updateEntity(EntityKind.PLAYER, steinitz.id(), clash));
    }
  }

  private static PlayerDto findPlayer(Database db, String lastName) {
    return db
        .findEntities(EntityKind.PLAYER, Query.of("name:" + lastName, Sort.natural(), 0, 1))
        .items()
        .getFirst();
  }

  private static List<Long> ids(ResultPage<GameDto> page) {
    return page.items().stream().map(GameDto::id).toList();
  }
}
