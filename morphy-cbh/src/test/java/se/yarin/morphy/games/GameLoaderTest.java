package se.yarin.morphy.games;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.chess.Date;
import se.yarin.chess.GameModel;
import se.yarin.morphy.Database;
import se.yarin.morphy.ResourceLoader;
import se.yarin.morphy.exceptions.MorphyException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GameLoaderTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private Database worldChDatabase;

    @Before
    public void setupWorldChDatabase() {
        worldChDatabase = ResourceLoader.openWorldChDatabase();
    }

    @Test
    public void getGameTest() throws MorphyException {
        GameModel gameModel = worldChDatabase.getGame(1).getModel();

        assertEquals("Zukertort, Johannes Hermann", gameModel.header().getWhite());
        assertEquals("Steinitz, William", gameModel.header().getBlack());
        assertEquals(new Date(1886, 1, 11), gameModel.header().getDate());
        assertEquals("World-ch01 Steinitz-Zukertort +10-5=5", gameModel.header().getEvent());
        assertNull(gameModel.header().getWhiteElo());
        assertNull(gameModel.header().getWhiteTeam());
    }
}