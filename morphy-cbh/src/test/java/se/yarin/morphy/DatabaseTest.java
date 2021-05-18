package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.chess.GameModel;
import se.yarin.chess.GameMovesModel;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.games.annotations.ImmutableTextAfterMoveAnnotation;
import se.yarin.morphy.util.CBUtil;
import se.yarin.morphy.validation.EntityStatsValidator;
import se.yarin.morphy.validation.GamesValidator;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static se.yarin.chess.Chess.D2;
import static se.yarin.chess.Chess.D4;

public class DatabaseTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private int countDatabaseFiles(@NotNull File file) {
        String baseName = CBUtil.baseName(file).toLowerCase(Locale.ROOT);
        return file.getParentFile().listFiles((dir, name) -> name.toLowerCase().startsWith(baseName)).length;
    }

    @Test
    public void openVeryOldDatabaseInReadOnly() throws IOException {
        // Open a database with only the minimum files, check that it works and nothing new is created
        File file = ResourceLoader.materializeDatabaseStream(getClass(), "database/veryold", "Mate2");
        int numFiles = countDatabaseFiles(file);

        Database.open(file, DatabaseMode.READ_ONLY);

        assertEquals(numFiles, countDatabaseFiles(file));
    }

    @Test
    public void openDatabaseInMemory() {
        // Ensures the context is the same in the newly opened database
        ResourceLoader.openWorldChDatabaseInMemory();
    }

    @Test
    public void openVeryOldDatabaseInMemory() throws IOException {
        // Open a database with only the minimum files; make sure no new files are created
        File file = ResourceLoader.materializeDatabaseStream(getClass(), "database/veryold", "Mate2");

        int numFiles = countDatabaseFiles(file);

        Database.open(file, DatabaseMode.IN_MEMORY);

        assertEquals(numFiles, countDatabaseFiles(file));
    }

    @Test
    public void openVeryOldDatabase() throws IOException {
        // Open a database with only the minimum files, check that it works and that the missing files are created
        File file = ResourceLoader.materializeDatabaseStream(getClass(), "database/veryold", "Mate2");

        int numFiles = countDatabaseFiles(file);
        assertFalse(CBUtil.fileWithExtension(file, ".cbj").exists());

        Database.open(file, DatabaseMode.READ_WRITE);

        assertTrue(CBUtil.fileWithExtension(file, ".cbj").exists());
        assertTrue(countDatabaseFiles(file) > numFiles);
    }

    @Test(expected = IOException.class)
    public void openDatabaseWithMissingEssentialFiles() throws IOException {
        // Try opening the database without the source index should fail
        List<String> extensions = Database.MANDATORY_EXTENSIONS.stream().filter(x -> !x.equals(".cbs")).collect(Collectors.toList());
        File file = ResourceLoader.materializeDatabaseStream(getClass(), "database/veryold", "Mate2", extensions);
        Database.open(file, DatabaseMode.READ_ONLY);
    }

    @Test
    public void openOldDatabaseInReadOnly() throws IOException {
        // Open a database with old version of cbj and cbtt file, check that it works and files are not upgraded
        File file = ResourceLoader.materializeDatabaseStream(getClass(), "database/old", "linares");
        int numFiles = countDatabaseFiles(file);
        long oldExtendedHeaderFileSize = CBUtil.fileWithExtension(file, ".cbj").length();
        long oldTournamentExtraFileSize = CBUtil.fileWithExtension(file, ".cbtt").length();

        Database.open(file, DatabaseMode.READ_ONLY);

        assertEquals(numFiles, countDatabaseFiles(file));
        assertEquals(oldExtendedHeaderFileSize, CBUtil.fileWithExtension(file, ".cbj").length());
        assertEquals(oldTournamentExtraFileSize, CBUtil.fileWithExtension(file, ".cbtt").length());
    }

    @Test
    public void openOldDatabase() throws IOException {
        // Open a database with old version of cbj and no cbtt file, check that it works and files are upgraded
        File file = ResourceLoader.materializeDatabaseStream(getClass(), "database/old", "linares");
        int numFiles = countDatabaseFiles(file);
        long oldFileSize = CBUtil.fileWithExtension(file, ".cbj").length();
        assertFalse(CBUtil.fileWithExtension(file, ".cbtt").exists());

        Database.open(file, DatabaseMode.READ_WRITE);

        assertEquals(numFiles + 1, countDatabaseFiles(file));  // the .cbtt file got created
        assertTrue(oldFileSize < CBUtil.fileWithExtension(file, ".cbj").length());
        assertTrue(CBUtil.fileWithExtension(file, ".cbtt").exists());
    }


    @Test(expected = MorphyInvalidDataException.class)
    public void getGameWithShorterExtendedHeaderStrict() throws IOException {
        // cbj file exists but contains fewer games than the cbh file
        // Database won't even open
        File cbh_cbj = ResourceLoader.materializeDatabaseStream(Database.class, "database/shorter_cbj_test", "shorter_cbj_test");
        Database.open(cbh_cbj, DatabaseMode.READ_ONLY);
    }

    @Test(expected = MorphyInvalidDataException.class)
    public void getGameAfterOpenInMemoryWithShorterExtendedHeaders() throws IOException {
        File cbh_cbj = ResourceLoader.materializeDatabaseStream(Database.class, "database/shorter_cbj_test", "shorter_cbj_test");
        Database.open(cbh_cbj, DatabaseMode.IN_MEMORY);
    }

    @Test
    public void createDatabase() throws IOException {
        File file = folder.newFile("new_db.cbh");
        file.delete();

        assertEquals(0, countDatabaseFiles(file));

        Database.create(file, false);

        assertEquals(16, countDatabaseFiles(file));
    }

    @Test(expected = IOException.class)
    public void createDatabaseButExists() throws IOException {
        File file = folder.newFile("new_db.cbh");

        assertEquals(1, countDatabaseFiles(file));

        Database.create(file, false);
    }

    @Test(expected = IOException.class)
    public void createDatabaseStrayFileExists() throws IOException {
        folder.newFile("my-Database.stray");
        File file = folder.newFile("MY-database.cbh");
        file.delete();

        assertEquals(1, countDatabaseFiles(file));

        Database.create(file, false);
    }

    @Test
    public void createAndOverwriteDatabase() throws IOException {
        File file = folder.newFile("new_db.cbh");
        File secondFile = folder.newFile("new_db.test");

        assertEquals(2, countDatabaseFiles(file));

        Database.create(file, true);

        assertEquals(16, countDatabaseFiles(file));
    }

    @Test
    public void deleteDatabase() throws IOException {
        File file = ResourceLoader.materializeStreamPath(Database.class, "database/World-ch");
        assertEquals(20, file.listFiles().length);

        Database.delete(new File(file, "World-ch.cbh"));

        assertEquals(0, file.listFiles().length);
    }

    @Test
    public void deletePartialDeletedDatabase() throws IOException {
        File file = ResourceLoader.materializeStreamPath(Database.class, "database/World-ch");
        File cbhFile = new File(file, "World-ch.cbh");
        cbhFile.delete();
        assertEquals(19, file.listFiles().length);

        Database.delete(cbhFile);

        assertEquals(0, file.listFiles().length);
    }


    @Test
    public void getSingleGame() {
        Database database = ResourceLoader.openWorldChDatabase();

        Game game = database.getGame(73);
        assertEquals("Chigorin, Mikhail Ivanovich", game.white().getFullName());
        assertEquals("Steinitz, William", game.black().getFullName());
        assertEquals("World-ch04 Steinitz-Chigorin +10-8=5", game.tournament().title());
    }

    @Test(expected = IllegalArgumentException.class)
    public void getGame0() {
        Database database = ResourceLoader.openWorldChDatabase();
        database.getGame(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getSingleMissingGame() {
        Database database = ResourceLoader.openWorldChDatabase();
        database.getGame(100000);
    }


    @Test
    public void addSingleGameToEmptyDatabase() throws IOException {
        Database db = new Database();

        GameModel gameModel = TestGames.getSimpleGame("Mardell", "Carlsen", "Sample tournament", null, null);

        db.addGame(gameModel);

        assertEquals(1, db.gameHeaderIndex().count());
        assertEquals(2, db.playerIndex().count());
        assertEquals(1, db.tournamentIndex().count());
        assertEquals(1, db.annotatorIndex().count());
        assertEquals(1, db.sourceIndex().count());

        Player p1 = db.playerIndex().get(0);
        Player p2 = db.playerIndex().get(1);
        Tournament t = db.tournamentIndex().get(0);

        assertEquals("Mardell", p1.lastName());
        assertEquals(1, p1.firstGameId());
        assertEquals(1, p1.count());

        assertEquals("Carlsen", p2.lastName());
        assertEquals(1, p2.firstGameId());
        assertEquals(1, p2.count());

        assertEquals("Sample tournament", t.title());
        assertEquals(1, t.firstGameId());
        assertEquals(1, t.count());

        assertEquals("", db.annotatorIndex().get(0).name());
        assertEquals("", db.sourceIndex().get(0).title());

        new EntityStatsValidator(db).validateEntityStatistics(true);

        db.close();
    }

    @Test
    public void addMultipleGames() throws IOException {
        Database db = new Database();

        db.addGame(TestGames.getSimpleGame("Mardell", "Carlsen", "t1", "my source", "myself"));
        db.addGame(TestGames.getSimpleGame("Kasparov", "Mardell", "t1", "", ""));
        db.addGame(TestGames.getSimpleGame("Karpov", "Fischer", "t2", null, ""));
        db.addGame(TestGames.getSimpleGame("Caruana", "Giri", "t2", "my source", "myself"));
        db.addGame(TestGames.getSimpleGame("Mardell", "Giri", "t3", null, ""));

        new EntityStatsValidator(db).validateEntityStatistics(true);

        db.close();
    }

    @Test
    public void replaceGame() throws IOException {
        Database db = new Database();

        db.addGame(TestGames.getSimpleGame("Mardell", "Carlsen", "t1", "my source", "myself"));
        db.addGame(TestGames.getSimpleGame("Kasparov", "Mardell", "t1", "", ""));

        db.replaceGame(2, TestGames.getSimpleGame("Karpov", "Fischer", "t2", null, ""));

        EntityStatsValidator validator = new EntityStatsValidator(db);
        validator.validateEntityStatistics(true);
        new GamesValidator(db).readAllGames();

        db.close();
    }

    @Test
    public void replaceGameCorrectlyUpdatesFirstGame() throws IOException {
        Database db = new Database();

        db.addGame(TestGames.getSimpleGame("a", "b"));
        db.addGame(TestGames.getSimpleGame("c", "c"));
        db.addGame(TestGames.getSimpleGame("d", "e"));
        db.addGame(TestGames.getSimpleGame("c", "a"));
        db.addGame(TestGames.getSimpleGame("b", "d"));

        // First game of c is still 2, first of d becomes 2
        db.replaceGame(2, TestGames.getSimpleGame("c", "d"));
        new EntityStatsValidator(db).validateEntityStatistics(true);

        // First game of a and c is unchanged
        db.replaceGame(4, TestGames.getSimpleGame("f", "g"));
        new EntityStatsValidator(db).validateEntityStatistics(true);

        // First game of b becomes 2
        db.replaceGame(1, TestGames.getSimpleGame("a", "d"));
        new EntityStatsValidator(db).validateEntityStatistics(true);

        db.close();
    }

    @Test
    public void replaceGameCausingMoveAdjustment() throws IOException {
        Database db = new Database();

        GameModel gameModel1 = TestGames.getSimpleGame("foo", "bar", "t1", "my source", "myself");
        GameModel gameModel2 = TestGames.getSimpleGame("foo", "bar", "t1", "my source", "myself");
        GameModel gameModel3 = TestGames.getSimpleGame("foo", "bar", "t1", "my source", "myself");
        gameModel3.moves().root().addMove(D2, D4);

        db.addGame(gameModel1);
        db.addGame(gameModel2);
        Game game2 = db.getGame(2);
        long oldMovesOffset = game2.getMovesOffset();

        db.replaceGame(1, gameModel3);

        Game game2New = db.getGame(2);
        long newMovesOffset = game2New.getMovesOffset();

        assertEquals(oldMovesOffset + 3, newMovesOffset);
        assertEquals(gameModel2.moves().root().toSAN(), game2New.getModel().moves().root().toSAN());

        db.close();
    }

    @Test
    public void replaceGameCausingAnnotationAdjustment() throws IOException {
        Database db = new Database();

        GameModel gameModel1 = TestGames.getSimpleGame("foo", "bar", "t1", "my source", "myself");
        GameModel gameModel2 = TestGames.getSimpleGame("foo", "bar", "t1", "my source", "myself");
        GameModel gameModel3 = TestGames.getSimpleGame("foo", "bar", "t1", "my source", "myself");
        gameModel1.moves().root().addAnnotation(ImmutableTextAfterMoveAnnotation.of("foo"));
        gameModel2.moves().root().addAnnotation(ImmutableTextAfterMoveAnnotation.of("blah"));
        gameModel3.moves().root().addAnnotation(ImmutableTextAfterMoveAnnotation.of("foobar"));

        db.addGame(gameModel1);
        db.addGame(gameModel2);
        Game game2 = db.getGame(2);
        long oldAnnotationOffset = game2.getAnnotationOffset();

        db.replaceGame(1, gameModel3);

        Game game2New = db.getGame(2);
        long newAnnotationOffset = game2New.getAnnotationOffset();

        assertEquals(oldAnnotationOffset + 3, newAnnotationOffset);
        System.out.println(gameModel2.moves().root().toSAN());

        // Make sure we can still read the annotation after the game has been moved
        String annotationText = ((ImmutableTextAfterMoveAnnotation) gameModel2.moves().root().getAnnotations().get(1)).text();
        assertEquals("blah", annotationText);

        db.close();
    }

    @Test
    public void replaceGameCausingInsertionOfAnnotation() throws IOException {
        Database db = new Database();

        GameModel gameModel1 = TestGames.getSimpleGame("foo", "bar", "t1", "my source", "myself");
        GameModel gameModel2 = TestGames.getSimpleGame("foo", "bar", "t1", "my source", "myself");
        GameModel gameModel3 = TestGames.getSimpleGame("foo", "bar", "t1", "my source", "myself");
        gameModel1.moves().root().deleteAnnotations();
        gameModel2.moves().root().addAnnotation(ImmutableTextAfterMoveAnnotation.of("blah"));
        gameModel3.moves().root().addAnnotation(ImmutableTextAfterMoveAnnotation.of("foobar"));

        db.addGame(gameModel1);
        db.addGame(gameModel2);
        Game game2 = db.getGame(2);
        long oldAnnotationOffset = game2.getAnnotationOffset();
        assertEquals(0, db.getGame(1).getAnnotationOffset()); // Ensure no annotations

        db.replaceGame(1, gameModel3);

        assertNotEquals(0, db.getGame(1).getAnnotationOffset()); // Now there should be!

        Game game2New = db.getGame(2);
        long newAnnotationOffset = game2New.getAnnotationOffset();

        assertEquals(oldAnnotationOffset + 53, newAnnotationOffset);

        // Make sure we can still read the annotation after the game has been moved
        String annotationText = ((ImmutableTextAfterMoveAnnotation) gameModel2.moves().root().getAnnotations().get(1)).text();
        assertEquals("blah", annotationText);

        db.close();
    }


    @Test
    public void randomlyAddAndReplaceGames() {
        Random random = new Random(0);
        GameGenerator gameGenerator = new GameGenerator();

        for (int iter = 0; iter < 10; iter++) {
            Database db = new Database();

            int noOps = 100, maxGames = 20;

            for (int i = 0; i < noOps; i++) {
                int gameId = random.nextInt(maxGames) + 1;
                GameModel game = gameGenerator.getRandomGame();

                if (gameId <= db.gameHeaderIndex().count()) {
                    db.replaceGame(gameId, game);
                } else {
                    db.addGame(game);
                }

                EntityStatsValidator validator = new EntityStatsValidator(db);
                GamesValidator gamesValidator = new GamesValidator(db);

                validator.validateEntityStatistics(true);
                // TODO: Validate "unused bytes" in moves/annotations headers
                gamesValidator.validateMovesAndAnnotationOffsets();
                gamesValidator.readAllGames();
            }
        }
    }

    @Test
    public void upgradeOldDatabaseAndWrite() throws IOException {
        // This tests that we can write to MoveRepository and AnnotationRepository
        // despite their headers being in the old format (they should not be upgraded)
        File file = ResourceLoader.materializeDatabaseStream(getClass(), "database/old", "linares");

        Database db = Database.open(file, DatabaseMode.READ_WRITE);
        assertEquals(10, db.moveRepository().getStorage().getHeader().headerSize());
        assertEquals(10, db.annotationRepository().getStorage().getHeader().headerSize());

        GameModel simpleGame = TestGames.getSimpleGame("foo", "bar");
        int id = db.addGame(simpleGame);

        Game game = db.getGame(id);
        String movesText = game.getModel().moves().root().toSAN();
        assertEquals("1.e4 c5", movesText);
    }

    // TODO: Some tests when opening a database where the filenames have different casing
    // This is not uncommon on Windows system

}
