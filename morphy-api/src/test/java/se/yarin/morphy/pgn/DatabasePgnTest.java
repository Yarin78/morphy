package se.yarin.morphy.pgn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.chess.GameResult;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.DatabaseFormat;
import se.yarin.morphy.api.Databases;
import se.yarin.morphy.api.EntityKind;
import se.yarin.morphy.api.GameFetchOptions;
import se.yarin.morphy.api.query.Query;
import se.yarin.morphy.api.query.ResultPage;
import se.yarin.morphy.api.query.Sort;
import se.yarin.morphy.model.AnnotationDto;
import se.yarin.morphy.model.GameDto;
import se.yarin.morphy.model.PlayerDto;

public class DatabasePgnTest {
  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private static String game(
      String white, String black, String result, String date, String extra, String moves) {
    return "[Event \"Some Open\"]\n"
        + "[Site \"Oslo\"]\n"
        + "[Date \"" + date + "\"]\n"
        + "[Round \"3\"]\n"
        + "[White \"" + white + "\"]\n"
        + "[Black \"" + black + "\"]\n"
        + "[Result \"" + result + "\"]\n"
        + extra
        + "\n"
        + moves + " " + result + "\n\n";
  }

  private static final String G1 =
      game("Carlsen, Magnus", "Nakamura, Hikaru", "1-0", "2020.01.10",
          "[WhiteElo \"2850\"]\n[BlackElo \"2780\"]\n[ECO \"C65\"]\n[Termination \"normal\"]\n",
          "1. e4 e5 2. Nf3 Nc6 3. Bb5 Nf6 {a comment} 4. O-O Nxe4");
  private static final String G2 =
      game("Kasparov, Garry", "Carlsen, Magnus", "1/2-1/2", "2019.05.02",
          "[WhiteElo \"2800\"]\n[BlackElo \"2830\"]\n[ECO \"D35\"]\n",
          "1. d4 d5 2. c4 e6 3. Nc3 Nf6");
  private static final String G3 =
      game("Anand, Viswanathan", "Kramnik, Vladimir", "0-1", "2021.07.20",
          "[WhiteElo \"2790\"]\n[BlackElo \"2810\"]\n[ECO \"A45\"]\n",
          "1. d4 Nf6 2. Bg5 Ne4 3. Bf4");

  private Path write(String contents) throws IOException {
    return write(contents.getBytes(StandardCharsets.UTF_8));
  }

  private Path write(byte[] contents) throws IOException {
    Path path = tmp.getRoot().toPath().resolve("games.pgn");
    Files.write(path, contents);
    return path;
  }

  private Database open(Path path) throws IOException {
    return DatabasePgn.open(path, AccessMode.READ_WRITE);
  }

  private static GameDto get(Database db, long id) {
    return db.getGame(id, GameFetchOptions.full());
  }

  /** A copy of a game with some fields changed. */
  private static GameDto variant(
      GameDto g, String type, Integer whiteElo, GameResult result, Map<String, String> extraTags) {
    return g.toBuilder()
        .id(null)
        .type(type)
        .textTitle(null)
        .whiteElo(whiteElo)
        .result(result)
        .extraTags(extraTags)
        .build();
  }

  private static List<Long> ids(ResultPage<GameDto> page) {
    return page.items().stream().map(GameDto::id).toList();
  }

  private static ResultPage<GameDto> find(Database db, String filter, String sort) {
    return db.findGames(Query.of(filter, Sort.parse(sort), 0, 100), GameFetchOptions.headersOnly());
  }

  // ── Reading ──────────────────────────────────────────────────────────────

  @Test
  public void describesItself() throws IOException {
    try (Database db = open(write(G1 + G2))) {
      assertEquals("games", db.name());
      assertEquals(DatabaseFormat.PGN, db.format());
      assertTrue(db.capabilities().canWrite());
      assertFalse(db.capabilities().hasEntities());
      assertEquals(2, db.gameCount());
    }
  }

  @Test
  public void readsHeaderOfAGame() throws IOException {
    try (Database db = open(write(G1 + G2 + G3))) {
      GameDto game = db.getGame(1, GameFetchOptions.headersOnly());
      assertEquals(1L, (long) game.id());
      assertEquals("game", game.type());
      assertEquals("Carlsen", game.whitePlayer().lastName());
      assertEquals("Magnus", game.whitePlayer().firstName());
      assertNull(game.whitePlayer().id());
      assertEquals("Nakamura", game.blackPlayer().lastName());
      assertEquals(2850, (int) game.whiteElo());
      assertEquals(GameResult.WHITE_WINS, game.result());
      assertEquals("2020.01.10", game.date().toString());
      assertEquals("C65", game.eco());
      assertEquals(3, (int) game.round());
      assertEquals("Some Open", game.tournament().title());
      assertEquals("Oslo", game.tournament().place());
      assertNull(game.moves());
      assertEquals(Map.of("Termination", "normal"), game.extraTags());
    }
  }

  @Test
  public void readsMovesOfAGame() throws IOException {
    try (Database db = open(write(G1))) {
      GameDto game = get(db, 1);
      assertEquals("1. e4 e5 2. Nf3 Nc6 3. Bb5 Nf6 4. O-O Nxe4", game.moves().pgn());
      assertEquals(
          List.of(new AnnotationDto.TextAfter(5, "a comment")), game.moves().annotations());
      assertNull(game.moves().fen());
      assertNotNull(game.notation());
    }
  }

  @Test
  public void readsAGameFromASetupPosition() throws IOException {
    String fen = "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1";
    String pgn =
        game("A", "B", "*", "2020.01.01", "[SetUp \"1\"]\n[FEN \"" + fen + "\"]\n", "1. e4 Kd7");
    try (Database db = open(write(pgn))) {
      assertEquals(true, db.getGame(1, GameFetchOptions.headersOnly()).setupPosition());
      GameDto game = get(db, 1);
      assertEquals(fen, game.moves().fen());
      assertNull(game.extraTags());
    }
  }

  @Test
  public void gamesOutsideTheFileDontExist() throws IOException {
    try (Database db = open(write(G1))) {
      assertNull(db.getGame(0, GameFetchOptions.full()));
      assertNull(db.getGame(2, GameFetchOptions.full()));
    }
  }

  @Test
  public void aGameWithBrokenMovesCanOnlyBeReadWithoutThem() throws IOException {
    String broken = game("Broken", "Game", "1-0", "2020.01.01", "", "1. e4 e5 2. Qxh7");
    try (Database db = open(write(G1 + broken + G3))) {
      assertEquals(3, db.gameCount());
      assertEquals("Broken", db.getGame(2, GameFetchOptions.headersOnly()).whitePlayer().lastName());
      assertThrows(IllegalStateException.class, () -> get(db, 2));
      assertEquals(List.of(1L, 2L, 3L), ids(find(db, null, "id")));
    }
  }

  @Test
  public void aGameWithABrokenHeaderDoesNotStopASearch() throws IOException {
    String broken = game("Broken", "Game", "banana", "2020.01.01", "", "1. e4 e5");
    try (Database db = open(write(G1 + broken + G3))) {
      assertEquals(3, db.gameCount());
      assertThrows(IllegalStateException.class, () -> db.getGame(2, GameFetchOptions.headersOnly()));
      assertEquals(List.of(1L, 3L), ids(find(db, "round:3", "id")));
    }
  }

  @Test
  public void unknownRatingsAreLeftOut() throws IOException {
    String pgn =
        game("A", "B", "*", "2020.01.01", "[WhiteElo \"?\"]\n[BlackElo \"-\"]\n", "1. e4 e5");
    try (Database db = open(write(pgn))) {
      GameDto game = db.getGame(1, GameFetchOptions.headersOnly());
      assertNull(game.whiteElo());
      assertNull(game.blackElo());
    }
  }

  @Test
  public void readsWindows1252GamesAmongUtf8Ones() throws IOException {
    String utf8 = game("Björn", "X", "*", "2020.01.01", "", "1. e4 e5");
    String latin1 = game("René", "Y", "*", "2020.01.02", "", "1. d4 d5");
    Charset windows1252 = Charset.forName("windows-1252");
    byte[] a = utf8.getBytes(StandardCharsets.UTF_8);
    byte[] b = latin1.getBytes(windows1252);
    byte[] all = new byte[a.length + b.length];
    System.arraycopy(a, 0, all, 0, a.length);
    System.arraycopy(b, 0, all, a.length, b.length);

    try (Database db = open(write(all))) {
      assertEquals("Björn", db.getGame(1, GameFetchOptions.headersOnly()).whitePlayer().lastName());
      assertEquals("René", db.getGame(2, GameFetchOptions.headersOnly()).whitePlayer().lastName());
    }
  }

  @Test
  public void readsAFileWithByteOrderMarkAndCrlf() throws IOException {
    byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    byte[] body = (G1 + G2).replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
    byte[] all = new byte[bom.length + body.length];
    System.arraycopy(bom, 0, all, 0, bom.length);
    System.arraycopy(body, 0, all, bom.length, body.length);

    try (Database db = open(write(all))) {
      assertEquals(2, db.gameCount());
      assertEquals("Carlsen", get(db, 1).whitePlayer().lastName());
      assertEquals("Kasparov", get(db, 2).whitePlayer().lastName());
    }
  }

  @Test
  public void createsTheIndexNextToTheFile() throws IOException {
    Path pgn = write(G1 + G2);
    try (Database db = open(pgn)) {
      assertEquals(2, db.gameCount());
    }
    assertTrue(Files.exists(tmp.getRoot().toPath().resolve("games.pgi")));
  }

  @Test
  public void aReadOnlyDatabaseWritesNothing() throws IOException {
    Path pgn = write(G1 + G2);
    try (Database db = DatabasePgn.open(pgn, AccessMode.READ_ONLY)) {
      assertEquals(2, db.gameCount());
      assertFalse(db.capabilities().canWrite());
      GameDto game = get(db, 1);
      assertThrows(UnsupportedOperationException.class, () -> db.addGame(game));
      assertThrows(UnsupportedOperationException.class, () -> db.replaceGame(1, game));
    }
    assertFalse(Files.exists(tmp.getRoot().toPath().resolve("games.pgi")));
  }

  @Test
  public void cantBeOpenedInMemory() throws IOException {
    Path pgn = write(G1);
    assertThrows(
        UnsupportedOperationException.class, () -> DatabasePgn.open(pgn, AccessMode.IN_MEMORY));
  }

  // ── Searching ────────────────────────────────────────────────────────────

  @Test
  public void listsGamesInIdOrderInPages() throws IOException {
    try (Database db = open(write(G1 + G2 + G3))) {
      ResultPage<GameDto> page =
          db.findGames(Query.all(1, 1), GameFetchOptions.headersOnly());
      assertEquals(List.of(2L), ids(page));
      assertEquals(3L, (long) page.total());

      ResultPage<GameDto> last = db.findGames(new Query(null, List.of(), Sort.parse("-id"), 0, 2), GameFetchOptions.headersOnly());
      assertEquals(List.of(3L, 2L), ids(last));
    }
  }

  @Test
  public void searchesPlayersByNameAndPosition() throws IOException {
    try (Database db = open(write(G1 + G2 + G3))) {
      assertEquals(List.of(1L, 2L), ids(find(db, "Carlsen", "id")));
      assertEquals(List.of(1L, 2L), ids(find(db, "player.name:carl", "id")));
      assertEquals(List.of(1L), ids(find(db, "player.name:Carlsen,position=white", "id")));
      assertEquals(List.of(2L), ids(find(db, "black.name:Carlsen", "id")));
      assertEquals(List.of(1L), ids(find(db, "winner:Carlsen", "id")));
      assertEquals(List.of(1L, 2L), ids(find(db, "player.name:\"Carlsen, Mag\"", "id")));
      assertEquals(List.of(), ids(find(db, "player.name:\"Carlsen, Hik\"", "id")));
      assertEquals(List.of(1L, 3L), ids(find(db, "player.name:Nakamura|Anand", "id")));
    }
  }

  @Test
  public void searchesOnGameFields() throws IOException {
    try (Database db = open(write(G1 + G2 + G3))) {
      assertEquals(List.of(1L), ids(find(db, "result:1-0", "id")));
      assertEquals(List.of(2L), ids(find(db, "result:draw", "id")));
      assertEquals(List.of(1L, 2L), ids(find(db, "rating:2820..", "id")));
      assertEquals(List.of(1L), ids(find(db, "rating:2850", "id")));
      assertEquals(List.of(1L, 2L), ids(find(db, "rating:2800..2900,mode=white", "id")));
      assertEquals(List.of(3L), ids(find(db, "eco:A*", "id")));
      assertEquals(List.of(1L), ids(find(db, "eco:C60..C99", "id")));
      assertEquals(List.of(1L, 2L, 3L), ids(find(db, "date:2019..2021", "id")));
      assertEquals(List.of(2L), ids(find(db, "date:2019", "id")));
      assertEquals(List.of(1L, 2L, 3L), ids(find(db, "round:3", "id")));
      assertEquals(List.of(1L, 2L, 3L), ids(find(db, "tournament:Some", "id")));
      assertEquals(List.of(), ids(find(db, "deleted:true", "id")));
      assertEquals(List.of(1L, 2L, 3L), ids(find(db, "type:game", "id")));
    }
  }

  @Test
  public void totalIsTheNumberOfMatchesNotThePageSize() throws IOException {
    try (Database db = open(write(G1 + G2 + G3))) {
      ResultPage<GameDto> page =
          db.findGames(Query.of("rating:2820..", Sort.natural(), 0, 1), GameFetchOptions.headersOnly());
      assertEquals(1, page.items().size());
      assertEquals(2L, (long) page.total());
      assertEquals("rating:2820..", page.appliedFilter());
    }
  }

  @Test
  public void sortsResults() throws IOException {
    try (Database db = open(write(G1 + G2 + G3))) {
      // Dates are descending unless told otherwise
      assertEquals(List.of(3L, 1L, 2L), ids(find(db, null, "date")));
      assertEquals(List.of(2L, 1L, 3L), ids(find(db, null, "+date")));
      assertEquals(List.of(3L, 1L, 2L), ids(find(db, null, "whitePlayerName")));
      // Ratings are descending unless told otherwise
      assertEquals(List.of(1L, 2L, 3L), ids(find(db, null, "whiteElo")));
      assertEquals(List.of(3L, 2L, 1L), ids(find(db, null, "+whiteElo")));
      assertEquals(List.of(1L, 2L, 3L), ids(find(db, null, "eloMax")));
    }
  }

  @Test
  public void searchesFetchMovesOnRequest() throws IOException {
    try (Database db = open(write(G1 + G2))) {
      ResultPage<GameDto> page =
          db.findGames(Query.of("Kasparov", Sort.natural(), 0, 10), GameFetchOptions.full());
      assertEquals(1, page.items().size());
      assertEquals("1. d4 d5 2. c4 e6 3. Nc3 Nf6", page.items().get(0).moves().pgn());
    }
  }

  @Test
  public void rejectsUnknownFieldsAndInvalidValues() throws IOException {
    try (Database db = open(write(G1))) {
      assertThrows(IllegalArgumentException.class, () -> find(db, "bogus:1", "id"));
      assertThrows(IllegalArgumentException.class, () -> find(db, null, "bogus"));
      assertThrows(IllegalArgumentException.class, () -> find(db, "result:maybe", "id"));
      assertThrows(IllegalArgumentException.class, () -> find(db, "rating:abc", "id"));
      // Even in an empty database
      Path other = tmp.getRoot().toPath().resolve("empty.pgn");
      try (Database empty = DatabasePgn.create(other)) {
        assertThrows(IllegalArgumentException.class, () -> find(empty, "bogus:1", "id"));
      }
    }
  }

  @Test
  public void schemaListsWhatCanBeSearched() throws IOException {
    try (Database db = open(write(G1))) {
      assertEquals("player.name", db.gameSearchSchema().defaultField());
      assertTrue(db.gameSearchSchema().fields().contains("rating"));
      assertTrue(db.gameSearchSchema().sortFields().stream().anyMatch(f -> f.name().equals("playedDate")));
    }
  }

  @Test
  public void hasNoEntities() throws IOException {
    try (Database db = open(write(G1))) {
      assertEquals(0, db.entityCount(EntityKind.PLAYER));
      assertNull(db.getEntity(EntityKind.PLAYER, 1));
      assertTrue(db.findEntities(EntityKind.PLAYER, Query.all(0, 10)).items().isEmpty());
    }
  }

  // ── Writing ──────────────────────────────────────────────────────────────

  @Test
  public void addedGameIsAppendedAndSurvivesReopening() throws IOException {
    Path pgn = write(G1 + G2);
    byte[] before = Files.readAllBytes(pgn);
    try (Database db = open(pgn)) {
      GameDto game = get(db, 1);
      assertEquals(3L, db.addGame(game));
      assertEquals(3, db.gameCount());
      assertEquals("Carlsen", get(db, 3).whitePlayer().lastName());
    }
    byte[] after = Files.readAllBytes(pgn);
    assertArrayEquals(before, Arrays.copyOf(after, before.length));

    try (Database db = open(pgn)) {
      assertEquals(3, db.gameCount());
      assertEquals(get(db, 1).moves(), get(db, 3).moves());
    }
  }

  @Test
  public void addedGameGetsABlankLineBeforeItEvenIfTheFileHadNone() throws IOException {
    Path pgn = write(G1.stripTrailing());
    try (Database db = open(pgn)) {
      db.addGame(get(db, 1));
      assertEquals(2, db.gameCount());
    }
    try (Database db = open(pgn)) {
      assertEquals(2, db.gameCount());
    }
  }

  @Test
  public void gamesCanBeAddedToANewDatabase() throws IOException {
    Path pgn = tmp.getRoot().toPath().resolve("new.pgn");
    Database source = open(write(G1 + G2));
    try (Database db = Databases.create(pgn.toFile())) {
      assertEquals(0, db.gameCount());
      assertEquals(1L, db.addGame(get(source, 2)));
      assertEquals(2L, db.addGame(get(source, 1)));
    } finally {
      source.close();
    }
    try (Database db = Databases.open(pgn.toFile())) {
      assertEquals(2, db.gameCount());
      assertEquals("Kasparov", get(db, 1).whitePlayer().lastName());
    }
    assertThrows(FileAlreadyExistsException.class, () -> DatabasePgn.create(pgn));
  }

  @Test
  public void replacingAGameLeavesTheOthersUntouched() throws IOException {
    Path pgn = write(G1 + G2 + G3);
    byte[] first = G1.getBytes(StandardCharsets.UTF_8);
    byte[] last = G3.getBytes(StandardCharsets.UTF_8);
    try (Database db = open(pgn)) {
      GameDto replacement = get(db, 3);
      db.replaceGame(2, replacement);
      assertEquals(3, db.gameCount());
      assertEquals("Anand", get(db, 2).whitePlayer().lastName());
      assertEquals(get(db, 3).moves(), get(db, 2).moves());
    }
    byte[] after = Files.readAllBytes(pgn);
    assertArrayEquals(first, Arrays.copyOf(after, first.length));
    assertArrayEquals(last, Arrays.copyOfRange(after, after.length - last.length, after.length));

    // The rewritten index matches, so it is used
    try (Database db = open(pgn)) {
      assertEquals(3, db.gameCount());
      assertEquals("Carlsen", get(db, 1).whitePlayer().lastName());
      assertEquals("Anand", get(db, 3).whitePlayer().lastName());
    }
  }

  @Test
  public void replacingAGameKeepsItsExtraTagsAndAnnotations() throws IOException {
    Path pgn = write(G1 + G2);
    try (Database db = open(pgn)) {
      GameDto before = get(db, 1);
      db.replaceGame(1, before);
      GameDto after = get(db, 1);
      assertEquals(before.extraTags(), after.extraTags());
      assertEquals("normal", after.extraTags().get("Termination"));
      assertEquals(before.moves(), after.moves());
      assertEquals(before.whiteElo(), after.whiteElo());
    }
  }

  @Test
  public void aPlyCountIsKeptCorrectButNeverAdded() throws IOException {
    String withPlyCount = game("A", "B", "*", "2020.01.01", "[PlyCount \"2\"]\n", "1. e4 e5");
    Path pgn = write(withPlyCount + G2);
    try (Database db = open(pgn)) {
      GameDto shorter = get(db, 1);
      assertEquals("2", shorter.extraTags().get("PlyCount"));

      // Longer moves: the ply count follows
      GameDto longer = variant(shorter, "game", null, GameResult.NOT_FINISHED, shorter.extraTags());
      longer = GameDto.builder()
          .id(longer.id())
          .type(longer.type())
          .whitePlayer(longer.whitePlayer())
          .blackPlayer(longer.blackPlayer())
          .result(longer.result())
          .date(longer.date())
          .round(longer.round())
          .tournament(longer.tournament())
          .moves(new se.yarin.morphy.model.GameMovesDto("1. e4 e5 2. Nf3 Nc6"))
          .extraTags(longer.extraTags())
          .build();
      db.replaceGame(1, longer);
      assertEquals("4", get(db, 1).extraTags().get("PlyCount"));

      // A game that had none doesn't get one
      db.replaceGame(2, get(db, 2));
      assertNull(get(db, 2).extraTags());
    }
  }

  @Test
  public void replacingAGameChangesIt() throws IOException {
    Path pgn = write(G1 + G2);
    try (Database db = open(pgn)) {
      GameDto g = get(db, 2);
      GameDto changed = variant(g, g.type(), 2900, GameResult.WHITE_WINS, Map.of("Mood", "great"));
      db.replaceGame(2, changed);
      GameDto after = get(db, 2);
      assertEquals(2900, (int) after.whiteElo());
      assertEquals(GameResult.WHITE_WINS, after.result());
      assertEquals(Map.of("Mood", "great"), after.extraTags());
      assertEquals(g.moves(), after.moves());
    }
  }

  @Test
  public void writesWithTheLineEndingOfTheFile() throws IOException {
    Path pgn = write((G1 + G2).replace("\n", "\r\n"));
    try (Database db = open(pgn)) {
      db.addGame(get(db, 1));
      db.replaceGame(2, get(db, 2));
    }
    String text = Files.readString(pgn);
    assertFalse(text.replace("\r\n", "").contains("\n"));
  }

  @Test
  public void replacingWithARegularGameKeepsChess960Games() throws IOException {
    String pgn960 =
        game("A", "B", "*", "2020.01.01",
            "[Variant \"Chess960\"]\n[SetUp \"1\"]\n[FEN \"bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w KQkq - 0 1\"]\n",
            "1. e4 e5");
    try (Database db = open(write(pgn960 + G1))) {
      GameDto before = get(db, 1);
      assertEquals("Chess960", before.variant());
      db.replaceGame(1, before);
      GameDto after = get(db, 1);
      assertEquals("Chess960", after.variant());
      assertEquals(before.moves(), after.moves());
    }
  }

  @Test
  public void rejectsBadWrites() throws IOException {
    try (Database db = open(write(G1))) {
      GameDto game = get(db, 1);
      assertThrows(IllegalArgumentException.class, () -> db.replaceGame(2, game));
      assertThrows(IllegalArgumentException.class, () -> db.replaceGame(0, game));
      GameDto text = variant(game, "text", null, GameResult.NOT_FINISHED, null);
      assertThrows(IllegalArgumentException.class, () -> db.addGame(text));
      PlayerDto player = new PlayerDto(1L, "Carlsen", "Magnus", null, null, null);
      assertThrows(
          UnsupportedOperationException.class, () -> db.updateEntity(EntityKind.PLAYER, 1, player));
      assertEquals(1, db.gameCount());
    }
  }

  // ── Opening ──────────────────────────────────────────────────────────────

  @Test
  public void databasesOpenPgnFiles() throws IOException {
    Path pgn = write(G1);
    try (Database db = Databases.open(pgn.toFile(), AccessMode.READ_ONLY)) {
      assertEquals(DatabaseFormat.PGN, db.format());
      assertTrue(db instanceof DatabasePgn);
    }
  }

  @Test
  public void headerOnlyKeepsJustTheTags() {
    String header = DatabasePgn.headerOnly(G1);
    assertTrue(header.startsWith("[Event \"Some Open\"]\n"));
    assertFalse(header.contains("e4"));
    assertTrue(header.trim().endsWith("*"));
  }
}
