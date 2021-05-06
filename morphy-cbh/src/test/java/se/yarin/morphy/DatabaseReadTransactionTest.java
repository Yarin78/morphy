package se.yarin.morphy;

import org.junit.Test;
import se.yarin.chess.Date;
import se.yarin.morphy.text.TextLanguage;
import se.yarin.morphy.text.TextModel;

import java.util.Set;

import static org.junit.Assert.*;

public class DatabaseReadTransactionTest {
    @Test
    public void getGame() {
        Database database = ResourceLoader.openWorldChDatabase();

        try (DatabaseReadTransaction txn = new DatabaseReadTransaction(database)) {
            Game game = txn.getGame(10);

            assertFalse(game.guidingText());
            assertEquals(database, game.database());
            assertEquals(10, game.id());
            assertEquals("Steinitz, William", game.white().getFullName());
            assertEquals(new Date(1886, 02, 26), game.playedDate());
            assertEquals(20, game.tournament().rounds());
        }
    }

    @Test
    public void getText() {
        Database database = ResourceLoader.openWorldChDatabase();

        try (DatabaseReadTransaction txn = new DatabaseReadTransaction(database)) {
            String expected = "World-ch06 Lasker-Steinitz +10-2=5";
            Game text = txn.getGame(99);
            assertTrue(text.guidingText());
            assertTrue(text.tournament().title().startsWith(expected));

            TextModel model = text.getTextModel();
            assertTrue(model.header().tournament().startsWith(expected));
            assertEquals(Set.of(TextLanguage.GERMAN), model.contents().titleLanguages());
            assertEquals(expected, model.contents().getTitle(TextLanguage.GERMAN));
        }
    }

    @Test
    public void iterateGames() {
        Database database = ResourceLoader.openWorldChDatabase();

        int startId = 20;

        try (DatabaseReadTransaction txn = new DatabaseReadTransaction(database)) {
            Iterable<Game> games = txn.iterable(startId);
            int expectedId = startId;
            Game lastGame = null;
            for (Game game : games) {
                assertEquals(expectedId, game.id());
                expectedId += 1;
                lastGame = game;
            }
            assertNotNull(lastGame);
            assertEquals(database.count() + 1, expectedId);
            assertEquals("Carlsen, Magnus", lastGame.white().getFullName());
        }
    }
}
