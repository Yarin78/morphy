package se.yarin.morphy.boosters;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.chess.GameMovesModel;
import se.yarin.morphy.Database;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.Game;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.games.TopGamesStorage;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

public class GameEventStorageTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File gameEventsStorageFile;

    @Before
    public void setupGameEventsStorageTest() throws IOException {
        gameEventsStorageFile = folder.newFile("testbase.cbb");
        gameEventsStorageFile.delete();
    }

    @Test
    public void validateWorldCh() {
        Database db = ResourceLoader.openWorldChDatabase();
        GameEventStorage storage = db.gameEventStorage();
        assert storage != null;
        for (int gameId = 1; gameId <= db.count(); gameId++) {
            GameEvents gameEvents = storage.get(gameId);
            Game game = db.getGame(gameId);
            if (!game.guidingText()) {
                GameEvents deducedGameEvents = new GameEvents(db.getGameModel(gameId).moves());
                assertEquals(gameEvents, deducedGameEvents);
            }
        }
    }

    @Test
    public void createNew() throws IOException {
        GameEventStorage storage = GameEventStorage.create(gameEventsStorageFile, null);
        assertEquals(0, storage.count());
        storage.close();

        storage = GameEventStorage.open(gameEventsStorageFile, null);
        assertEquals(0, storage.count());
        storage.close();
    }

    @Test
    public void putSingleGame() throws IOException {
        GameEventStorage storage = GameEventStorage.create(gameEventsStorageFile, null);

        byte[] bytes = new byte[52];
        bytes[10] = 5;
        storage.put(1, new GameEvents(ByteBuffer.wrap(bytes)));
        assertEquals(1, storage.count());
        storage.close();

        storage = GameEventStorage.open(gameEventsStorageFile, null);
        assertEquals(1, storage.count());
        GameEvents gameEvents = storage.get(1);
        assertEquals(5, gameEvents.getBytes()[10]);
        storage.close();
    }

}
