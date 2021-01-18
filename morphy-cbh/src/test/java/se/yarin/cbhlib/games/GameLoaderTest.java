package se.yarin.cbhlib.games;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.ResourceLoader;
import se.yarin.cbhlib.entities.PlayerBase;
import se.yarin.cbhlib.exceptions.ChessBaseException;
import se.yarin.chess.Date;
import se.yarin.chess.GameModel;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GameLoaderTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private Database database;

    @Before
    public void setupWorldChDatabase() throws IOException {
        File file = ResourceLoader.materializeDatabaseStream(
                GameHeader.class,
                "World-ch/World-ch",
                folder.newFolder("World-ch"),
                "World-ch");
        database = Database.openInMemory(file);
    }

    @Test
    public void getGameTest() throws ChessBaseException {
        GameModel gameModel = database.getGameModel(1);

        assertEquals("Zukertort, Johannes Hermann", gameModel.header().getWhite());
        assertEquals("Steinitz, William", gameModel.header().getBlack());
        assertEquals(new Date(1886, 1, 11), gameModel.header().getDate());
        assertEquals("World-ch01 Steinitz-Zukertort +10-5=5", gameModel.header().getEvent());
        assertNull(gameModel.header().getWhiteElo());
        assertNull(gameModel.header().getWhiteTeam());
    }
}
