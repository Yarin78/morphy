package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.*;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.games.ImmutableExtendedGameHeader;
import se.yarin.morphy.games.ImmutableGameHeader;
import se.yarin.morphy.games.annotations.ImmutableTextAfterMoveAnnotation;
import se.yarin.morphy.validation.EntityStatsValidator;
import se.yarin.morphy.validation.GamesValidator;
import se.yarin.util.ByteBufferUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static se.yarin.chess.Chess.*;

public class DatabaseTransactionTest {
    private static final Logger log = LoggerFactory.getLogger(DatabaseTest.class);

    private GameModel getSimpleGame(String white, String black) {
        return getSimpleGame(white, black, "", "", "");
    }

    private GameModel getSimpleGame(String white, String black, String event, String source, String annotator) {
        GameHeaderModel header = new GameHeaderModel();
        header.setWhite(white);
        header.setBlack(black);
        header.setResult(GameResult.DRAW);
        header.setDate(new Date(2016, 8, 10));
        header.setEvent(event);
        header.setSourceTitle(source);
        header.setAnnotator(annotator);
        GameMovesModel movesModel = new GameMovesModel();
        movesModel.root().addMove(E2, E4).addMove(C7, C5);
        movesModel.root().addAnnotation(ImmutableTextAfterMoveAnnotation.builder().text("Interesting game!").build());

        GameModel gameModel = new GameModel(header, movesModel);
        return gameModel;
    }

    @Test
    public void addSingleGameToEmptyDatabase() throws IOException {
        Database db = new Database();

        GameModel gameModel = getSimpleGame("Mardell", "Carlsen", "Sample tournament", null, null);

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

        db.addGame(getSimpleGame("Mardell", "Carlsen", "t1", "my source", "myself"));
        db.addGame(getSimpleGame("Kasparov", "Mardell", "t1", "", ""));
        db.addGame(getSimpleGame("Karpov", "Fischer", "t2", null, ""));
        db.addGame(getSimpleGame("Caruana", "Giri", "t2", "my source", "myself"));
        db.addGame(getSimpleGame("Mardell", "Giri", "t3", null, ""));

        new EntityStatsValidator(db).validateEntityStatistics(true);

        db.close();
    }

    @Test
    public void replaceGame() throws IOException {
        Database db = new Database();

        db.addGame(getSimpleGame("Mardell", "Carlsen", "t1", "my source", "myself"));
        db.addGame(getSimpleGame("Kasparov", "Mardell", "t1", "", ""));

        db.replaceGame(2, getSimpleGame("Karpov", "Fischer", "t2", null, ""));

        EntityStatsValidator validator = new EntityStatsValidator(db);
        validator.validateEntityStatistics(true);
        new GamesValidator(db).readAllGames();

        db.close();
    }

    @Test
    public void replaceGameCorrectlyUpdatesFirstGame() throws IOException {
        Database db = new Database();

        db.addGame(getSimpleGame("a", "b"));
        db.addGame(getSimpleGame("c", "c"));
        db.addGame(getSimpleGame("d", "e"));
        db.addGame(getSimpleGame("c", "a"));
        db.addGame(getSimpleGame("b", "d"));

        // First game of c is still 2, first of d becomes 2
        db.replaceGame(2, getSimpleGame("c", "d"));
        new EntityStatsValidator(db).validateEntityStatistics(true);

        // First game of a and c is unchanged
        db.replaceGame(4, getSimpleGame("f", "g"));
        new EntityStatsValidator(db).validateEntityStatistics(true);

        // First game of b becomes 2
        db.replaceGame(1, getSimpleGame("a", "d"));
        new EntityStatsValidator(db).validateEntityStatistics(true);

        db.close();
    }

    @Test
    public void replaceGameCausingMoveAdjustment() throws IOException {
        Database db = new Database();

        GameModel gameModel1 = getSimpleGame("foo", "bar", "t1", "my source", "myself");
        GameModel gameModel2 = getSimpleGame("foo", "bar", "t1", "my source", "myself");
        GameModel gameModel3 = getSimpleGame("foo", "bar", "t1", "my source", "myself");
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

        GameModel gameModel1 = getSimpleGame("foo", "bar", "t1", "my source", "myself");
        GameModel gameModel2 = getSimpleGame("foo", "bar", "t1", "my source", "myself");
        GameModel gameModel3 = getSimpleGame("foo", "bar", "t1", "my source", "myself");
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

        GameModel gameModel1 = getSimpleGame("foo", "bar", "t1", "my source", "myself");
        GameModel gameModel2 = getSimpleGame("foo", "bar", "t1", "my source", "myself");
        GameModel gameModel3 = getSimpleGame("foo", "bar", "t1", "my source", "myself");
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
}
