package se.yarin.cbhlib;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.annotations.TextAfterMoveAnnotation;
import se.yarin.cbhlib.entities.EntityStatsValidator;
import se.yarin.cbhlib.entities.EntityStorageException;
import se.yarin.chess.*;

import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static se.yarin.chess.Chess.*;

public class DatabaseTest {
    private static final Logger log = LoggerFactory.getLogger(DatabaseTest.class);

    private GameModel getSimpleGame(String white, String black) {
        return getSimpleGame(white, black, "", "", "");
    }

    private GameModel getSimpleGame(String white, String black, String event, String source, String annotator) {
        GameHeaderModel header = new GameHeaderModel();
        header.setField("white", white);
        header.setField("black", black);
        header.setField("result", GameResult.DRAW);
        header.setField("date", new Date(2016, 8, 10));
        header.setField("event", event);
        header.setField("source", source);
        header.setField("annotator", annotator);
        GameMovesModel movesModel = new GameMovesModel();
        movesModel.root().addMove(E2, E4).addMove(C7, C5);
        movesModel.root().addAnnotation(new TextAfterMoveAnnotation("Interesting game!"));

        GameModel gameModel = new GameModel(header, movesModel);
        return gameModel;
    }

    @Test
    public void addSingleGameToEmptyDatabase() throws IOException, EntityStorageException {
        Database db = new Database();

        GameModel gameModel = getSimpleGame("Mardell", "Carlsen", "Sample tournament", null, null);

        db.addGame(gameModel);

        assertEquals(1, db.getHeaderBase().size());
        assertEquals(2, db.getPlayerBase().getCount());
        assertEquals(1, db.getTournamentBase().getCount());
        assertEquals(1, db.getAnnotatorBase().getCount());
        assertEquals(1, db.getSourceBase().getCount());

        PlayerEntity p1 = db.getPlayerBase().get(0);
        PlayerEntity p2 = db.getPlayerBase().get(1);
        TournamentEntity t = db.getTournamentBase().get(0);

        assertEquals("Mardell", p1.getLastName());
        assertEquals(1, p1.getFirstGameId());
        assertEquals(1, p1.getCount());

        assertEquals("Carlsen", p2.getLastName());
        assertEquals(1, p2.getFirstGameId());
        assertEquals(1, p2.getCount());

        assertEquals("Sample tournament", t.getTitle());
        assertEquals(1, t.getFirstGameId());
        assertEquals(1, t.getCount());

        assertEquals("", db.getAnnotatorBase().get(0).getName());
        assertEquals("", db.getSourceBase().get(0).getTitle());

        new EntityStatsValidator(db).validateEntityStatistics(true);

        db.close();
    }

    @Test
    public void addMultipleGames() throws IOException, EntityStorageException {
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
    public void replaceGame() throws IOException, EntityStorageException, ChessBaseException {
        Database db = new Database();

        db.addGame(getSimpleGame("Mardell", "Carlsen", "t1", "my source", "myself"));
        db.addGame(getSimpleGame("Kasparov", "Mardell", "t1", "", ""));

        db.replaceGame(2, getSimpleGame("Karpov", "Fischer", "t2", null, ""));

        EntityStatsValidator validator = new EntityStatsValidator(db);
        validator.validateEntityStatistics(true);
        validator.readAllGames();

        db.close();
    }

    @Test
    public void replaceGameCorrectlyUpdatesFirstGame() throws IOException, EntityStorageException {
        Database db = new Database();

        db.addGame(getSimpleGame("a", "b"));
        db.addGame(getSimpleGame("c", "c"));
        db.addGame(getSimpleGame("d", "e"));
        db.addGame(getSimpleGame("c", "a"));
        db.addGame(getSimpleGame("b", "d"));

        // First game of c is still 2, first of b becomes 2
        db.replaceGame(2, getSimpleGame("c", "b"));
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
    public void randomlyAddAndReplaceGames() throws IOException, EntityStorageException, ChessBaseException {
        Random random = new Random(0);
        GameGenerator gameGenerator = new GameGenerator();

        for (int iter = 0; iter < 10; iter++) {
            Database db = new Database();
            EntityStatsValidator validator = new EntityStatsValidator(db);

            int noOps = 100, maxGames = 20;

            for (int i = 0; i < noOps; i++) {
                int gameId = random.nextInt(maxGames) + 1;
                GameModel game = gameGenerator.getRandomGame();


                if (gameId <= db.getHeaderBase().size()) {
                    db.replaceGame(gameId, game);
                } else {
                    db.addGame(game);
                }

                validator.validateEntityStatistics(true);
                validator.validateMovesAndAnnotationOffsets();
                validator.readAllGames();
            }
        }
    }
}
