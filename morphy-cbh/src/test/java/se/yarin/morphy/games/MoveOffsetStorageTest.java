package se.yarin.morphy.games;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.morphy.Database;
import se.yarin.morphy.Game;
import se.yarin.morphy.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;

// TODO: CHUNK_SIZE should be really small, like 2, in these tests. Move to DatabaseConfig and reset in this test.
public class MoveOffsetStorageTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File moveOffsetStorageFile;

    @Before
    public void setupMoveOffsetStorageTest() throws IOException {
        moveOffsetStorageFile = folder.newFile("testbase.cbgi");
        moveOffsetStorageFile.delete();
    }

    @Test
    public void validateWorldCh() {
        Database db = ResourceLoader.openWorldChDatabase();
        MoveOffsetStorage moveOffsetStorage = db.moveOffsetStorage();
        assert moveOffsetStorage != null;

        for (int gameId = 1; gameId <= db.count(); gameId++) {
            Game game = db.getGame(gameId);
            if (!game.guidingText()) {
                long offset1 = moveOffsetStorage.getOffset(gameId);
                long offset2 = game.getMovesOffset();
                assertEquals(offset1, offset2);
            }
        }
    }

    @Test
    public void createEmptyStorage() throws IOException {
        MoveOffsetStorage storage = MoveOffsetStorage.create(moveOffsetStorageFile, null);
        assertEquals(0, storage.count());
        storage.close();

        storage = MoveOffsetStorage.open(moveOffsetStorageFile, null);
        assertEquals(0, storage.count());
        storage.close();
    }

    @Test
    public void addSingleGameOffset() throws IOException {
        MoveOffsetStorage storage = MoveOffsetStorage.create(moveOffsetStorageFile, null);
        storage.putOffset(1, 100);
        assertEquals(1, storage.count());
        assertEquals(100, storage.getOffset(1));
        storage.close();

        storage = MoveOffsetStorage.open(moveOffsetStorageFile, null);
        assertEquals(1, storage.count());
        assertEquals(100, storage.getOffset(1));
        storage.close();
    }

    @Test
    public void addManyOffsets() throws IOException {
        MoveOffsetStorage storage = MoveOffsetStorage.create(moveOffsetStorageFile, null);
        storage.putOffsets(Map.of(
                1, 100,
                2, 120,
                3, 150,
                4, 190,
                5, 230
        ));
        storage.putOffsets(Map.of(
                6, 240,
                7, 290,
                8, 340
        ));

        assertEquals(8, storage.count());
        assertEquals(150, storage.getOffset(3));
        assertEquals(340, storage.getOffset(8));
        storage.close();

        storage = MoveOffsetStorage.open(moveOffsetStorageFile, null);
        assertEquals(8, storage.count());
        assertEquals(150, storage.getOffset(3));
        storage.putOffsets(Map.of(
                9, 350,
                10, 390,
                11, 400
        ));
        assertEquals(11, storage.count());
        assertEquals(350, storage.getOffset(9));
        storage.close();

        storage = MoveOffsetStorage.open(moveOffsetStorageFile, null);
        assertEquals(390, storage.getOffset(10));
        storage.close();
    }

    @Test
    public void updateOldOffsets() throws IOException {
        MoveOffsetStorage storage = MoveOffsetStorage.create(moveOffsetStorageFile, null);
        storage.putOffsets(Map.of(
                1, 100,
                2, 120,
                3, 150,
                4, 190,
                5, 230)
        );
        storage.close();

        storage = MoveOffsetStorage.open(moveOffsetStorageFile, null);
        storage.putOffsets(Map.of(
                2, 122,
                4, 193,
                7, 300,
                6, 250));
        assertEquals(100, storage.getOffset(1));
        assertEquals(122, storage.getOffset(2));
        assertEquals(150, storage.getOffset(3));
        assertEquals(193, storage.getOffset(4));
        assertEquals(230, storage.getOffset(5));
        assertEquals(250, storage.getOffset(6));
        assertEquals(300, storage.getOffset(7));
        storage.close();

        storage = MoveOffsetStorage.open(moveOffsetStorageFile, null);
        assertEquals(122, storage.getOffset(2));
        assertEquals(150, storage.getOffset(3));
        assertEquals(250, storage.getOffset(6));
        storage.close();
    }

    @Test
    public void putBeyondLastGame() throws IOException {
        MoveOffsetStorage storage = MoveOffsetStorage.create(moveOffsetStorageFile, null);
        storage.putOffsets(Map.of(
                1, 100,
                2, 120));
        storage.putOffsets(Map.of(
                6, 150,
                7, 190));

        assertEquals(7, storage.count());
        assertEquals(120, storage.getOffset(2));
        assertEquals(0, storage.getOffset(3));
        assertEquals(0, storage.getOffset(5));
        assertEquals(150, storage.getOffset(6));
        storage.close();

        storage = MoveOffsetStorage.open(moveOffsetStorageFile, null);
        assertEquals(7, storage.count());
        assertEquals(120, storage.getOffset(2));
        assertEquals(0, storage.getOffset(3));
        assertEquals(0, storage.getOffset(5));
        assertEquals(150, storage.getOffset(6));
        storage.close();
    }

}
