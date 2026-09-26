package se.yarin.morphy.cb2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.yarin.chess.Date;
import se.yarin.chess.Eco;
import se.yarin.chess.GameHeaderModel;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.GameResult;
import se.yarin.chess.pgn.PgnParser;
import se.yarin.morphy.cb2.indexes.SortIndexFile.StandardIndex;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.cb2.entities.EntityType;
import se.yarin.morphy.cb2.entities.Player;
import se.yarin.morphy.cb2.games.GameHeader;
import se.yarin.morphy.cb2.indexes.GameListFile.Role;
import se.yarin.morphy.chessbase.annotations.AnnotationConverter;
import se.yarin.morphy.chessbase.annotations.TextAfterMoveAnnotation;
import se.yarin.morphy.chessbase.text.ImmutableTextHeaderModel;
import se.yarin.morphy.chessbase.text.ImmutableTextModel;
import se.yarin.morphy.chessbase.text.TextContentsModel;
import se.yarin.morphy.chessbase.text.TextLanguage;

/** Writing games, texts and entities keeps a database consistent, and reads back what was written. */
class WriteTransactionTest {

  @TempDir File tempDir;

  private static GameModel game(String white, String black, String event, String moves)
      throws Exception {
    GameHeaderModel header = new GameHeaderModel();
    header.setWhite(white);
    header.setBlack(black);
    header.setEvent(event);
    header.setEventSite("Stockholm");
    header.setEventDate(new Date(2024, 5, 1));
    header.setDate(new Date(2024, 5, 2));
    header.setResult(GameResult.WHITE_WINS);
    header.setWhiteElo(2700);
    header.setEco(new Eco("C42"));
    header.setRound(3);
    PgnParser parser =
        new PgnParser(AnnotationConverter.getRoundTripConverter()::convertToChessBase);
    return new GameModel(header, parser.parseMoves(moves));
  }

  @Test
  void writeToNewDatabase() throws Exception {
    try (Database2Cbh db = Database2Cbh.createInMemory("test")) {
      int id1 = db.addGame(game("Carlsen, Magnus", "Nepo, Ian", "WCh", "1. e4 e5 {Good} 2. Nf3 Nc6 (2... d6 $1) 3. Bb5 a6 1-0"));
      int id2 = db.addGame(game("Nepo, Ian", "Carlsen, Magnus", "WCh", "1. d4 d5 2. c4 1-0"));
      int id3 = db.addGame(game("Tal, Mikhail", "Carlsen, Magnus", "Other", "1. e4 c5 1-0"));
      assertEquals(List.of(1, 2, 3), List.of(id1, id2, id3));
      Consistency.check(db);

      try (ReadTransaction txn = new ReadTransaction(db)) {
        Game g = txn.getGame(1);
        GameHeader h = g.header();
        assertEquals("Carlsen", g.white().lastName());
        assertEquals("Magnus", g.white().firstName());
        assertEquals("WCh", g.tournament().title());
        assertEquals(GameResult.WHITE_WINS.ordinal(), h.result());
        assertEquals(2700, h.whiteElo());
        assertEquals(3, h.moveCount());
        assertEquals(1, h.version());
        assertEquals("1. e4 e5 2. Nf3 Nc6 (2... d6) 3. Bb5 a6", g.moves().toString());
        assertEquals(
            "Good",
            g.moves().root().mainNode().mainNode().getAnnotation(TextAfterMoveAnnotation.class).text());
        assertTrue((h.flags() & 2) != 0, "variations flag");
        // The same players and tournament are shared
        assertEquals(txn.getGame(2).header().blackId(), h.whiteId());
        assertEquals(txn.getGame(2).header().tournamentId(), h.tournamentId());
        assertNotEquals(txn.getGame(3).header().tournamentId(), h.tournamentId());
        // Blank fields refer to the placeholders, and no team is -1
        assertTrue(g.annotator().isEmpty());
        assertTrue(g.gameTag().isEmpty());
        assertEquals(-1, h.whiteTeamId());
        assertEquals(List.of(1, 2, 3), txn.gameIds(h.whiteId(), Role.PLAYER));
        assertEquals(3, txn.sortedCount(StandardIndex.PLAYERS));
      }
    }
  }

  @Test
  void replaceGamesAndEntitiesInACopyOfTheSample() throws Exception {
    File file = copySample();
    String untouched;
    try (Database2Cbh db = Database2Cbh.open(file, AccessMode.READ_WRITE)) {
      int count = db.count();
      // The game with the shortest record gets the moves of the one with the longest
      int small = 0, big = 0;
      for (int id = 1; id < count; id++) {
        if (db.gameHeaderFile().get(id) instanceof GameHeader g) {
          int length = db.moveFile().recordLength(g.movesOffset());
          if (small == 0 || length < db.moveFile().recordLength(db.gameHeaderFile().get(small).movesOffset())) {
            small = id;
          }
          if (big == 0 || length > db.moveFile().recordLength(db.gameHeaderFile().get(big).movesOffset())) {
            big = id;
          }
        }
      }
      GameMovesModel longMoves = db.getGameModel(big).moves();
      untouched = db.getGameModel(small + 1).moves().toString();

      GameModel before = db.getGameModel(small);
      long nextBefore = db.gameHeaderFile().get(small + 1).movesOffset();
      long sizeBefore = db.moveFile().size();
      db.replaceGame(small, new GameModel(before.header(), longMoves));
      Consistency.check(db);
      long moved = db.gameHeaderFile().get(small + 1).movesOffset() - nextBefore;
      assertTrue(moved > 0, "the next game should have moved");
      System.out.printf(
          "game %d moved %d bytes; the file grew %d bytes%n",
          small + 1, moved, db.moveFile().size() - sizeBefore);
      assertEquals(longMoves.toString(), db.getGameModel(small).moves().toString());
      assertEquals(untouched, db.getGameModel(small + 1).moves().toString());

      // The last game grows past the end of the file
      GameModel last = db.getGameModel(count);
      db.replaceGame(count, new GameModel(last.header(), longMoves));
      Consistency.check(db);

      // A game with new players and a new tournament; the old ones lose a game
      int sevenVersion;
      long oldWhite;
      int oldWhiteGames;
      try (ReadTransaction txn = new ReadTransaction(db)) {
        sevenVersion = txn.getGame(7).header().version();
        oldWhite = txn.getGame(7).header().whiteId();
        oldWhiteGames = txn.gameCount(oldWhite, Role.PLAYER);
      }
      GameModel renamed = game("Newcomer, Nils", "Other, Olga", "New Open", "1. e4 e5 1-0");
      db.replaceGame(7, renamed);
      Consistency.check(db);
      try (ReadTransaction txn = new ReadTransaction(db)) {
        assertEquals("Newcomer", txn.getGame(7).white().lastName());
        assertEquals(oldWhiteGames - 1, txn.gameCount(oldWhite, Role.PLAYER));
        assertEquals(sevenVersion + 1, txn.getGame(7).header().version());
      }

      // Adding games, one of them a text
      int added = db.addGame(game("Kasparov, Garry", "Karpov, Anatoly", "World-ch35", "1. d4 Nf6 1/2-1/2"));
      assertEquals(count + 1, added);
      TextContentsModel contents = new TextContentsModel();
      contents.setTitle(TextLanguage.ENGLISH, "About the match");
      contents.setContents(TextLanguage.ENGLISH, "<html><body>Hello</body></html>");
      int text =
          db.addText(
              ImmutableTextModel.builder()
                  .header(ImmutableTextHeaderModel.builder().tournament("World-ch35").annotator("Mardell, Jimmy").build())
                  .contents(contents)
                  .build());
      Consistency.check(db);
      assertEquals(
          "<html><body>Hello</body></html>",
          db.getTextModel(text).contents().getContents(TextLanguage.ENGLISH));
      assertEquals("About the match", db.getTextModel(text).contents().getTitle(TextLanguage.ENGLISH));
    }

    // Everything reads back after reopening
    try (Database2Cbh db = Database2Cbh.open(file, AccessMode.READ_WRITE)) {
      Consistency.check(db);
      assertEquals("Newcomer, Nils", db.getGameModel(7).header().getWhite());
    }
  }

  @Test
  void updateEntityResortsIt() throws Exception {
    File file = copySample();
    try (Database2Cbh db = Database2Cbh.open(file, AccessMode.READ_WRITE)) {
      long karpov;
      try (ReadTransaction txn = new ReadTransaction(db)) {
        karpov = txn.getGame(1).header().whiteId();
      }
      Player before = (Player) db.entityFile().get(EntityType.PLAYER, (int) karpov);
      try (WriteTransaction txn = new WriteTransaction(db)) {
        txn.updateEntity(karpov, before.withNames("Aaaaa", "Zed"));
        txn.commit();
      }
      Consistency.check(db);
      assertEquals("Aaaaa", ((Player) db.entityFile().get(EntityType.PLAYER, (int) karpov)).lastName());

      // Another player can't be given the same names
      long other;
      try (ReadTransaction txn = new ReadTransaction(db)) {
        other = txn.getGame(1).header().blackId();
      }
      try (WriteTransaction w = new WriteTransaction(db)) {
        assertThrows(
            IllegalArgumentException.class, () -> w.updateEntity(other, Player.of("Aaaaa", "Zed")));
      }
    }
  }

  @Test
  void transactionsSeeTheirOwnChangesAndCanRollBack() throws Exception {
    try (Database2Cbh db = Database2Cbh.createInMemory("test")) {
      try (WriteTransaction txn = new WriteTransaction(db)) {
        int id = txn.addGame(game("A, B", "C, D", "E", "1. e4 1-0"));
        assertEquals(1, txn.count());
        assertEquals("A", txn.getGame(id).white().lastName());
        assertEquals("1. e4", txn.getGame(id).moves().toString());
        assertEquals(0, db.count());
        txn.rollback();
        assertEquals(0, txn.count());
        assertFalse(txn.hasUncommittedChanges());
        txn.addGame(game("A, B", "C, D", "E", "1. e4 1-0"));
        txn.commit();
      }
      assertEquals(1, db.count());
      Consistency.check(db);

      // A commit fails if another one came first
      try (WriteTransaction txn = new WriteTransaction(db)) {
        txn.addGame(game("A, B", "C, D", "E", "1. d4 1-0"));
        db.context().bumpVersion();
        assertThrows(IllegalStateException.class, txn::commit);
      }
    }
  }

  @Test
  void aBoundEntityMustExist() throws Exception {
    try (Database2Cbh db = Database2Cbh.createInMemory("test")) {
      GameModel model = game("A, B", "C, D", "E", "1. e4 1-0");
      model.header().setWhiteId(42L);
      assertThrows(IllegalArgumentException.class, () -> db.addGame(model));
      assertEquals(0, db.count());
    }
  }

  @Test
  void readOnlyDatabasesRefuseWrites() throws Exception {
    try (Database2Cbh db = Database2Cbh.open(TestDatabases.wch2(), AccessMode.READ_ONLY)) {
      assertThrows(IllegalStateException.class, () -> new WriteTransaction(db));
    }
  }

  @Test
  void createOnDisk() throws Exception {
    File file = new File(tempDir, "fresh.2cbh");
    try (Database2Cbh db = Database2Cbh.create(file)) {
      Consistency.check(db);
      db.addGame(game("A, B", "C, D", "E", "1. e4 e5 1-0"));
    }
    try (Database2Cbh db = Database2Cbh.open(file, AccessMode.READ_ONLY)) {
      assertEquals(1, db.count());
      assertEquals(5, db.moveFile().version());
      Consistency.check(db);
    }
    assertThrows(IOException.class, () -> Database2Cbh.create(file));
    Database2Cbh.delete(file);
    assertFalse(file.exists());
  }

  private File copySample() throws IOException {
    File source = TestDatabases.wch2();
    for (String extension : Database2Cbh.EXTENSIONS) {
      Files.copy(TestDatabases.sibling(source, extension).toPath(), new File(tempDir, "wch2" + extension).toPath());
    }
    return new File(tempDir, "wch2.2cbh");
  }
}
