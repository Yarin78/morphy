package se.yarin.cbhlib;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PersistentGameHeaderStorageTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File gameHeaderFile;

    @Before
    public void setupEntityTest() throws IOException {
        gameHeaderFile = materializeStream(this.getClass().getResourceAsStream("cbhlib_test.cbh"));
    }

    private File materializeStream(InputStream stream) throws IOException {
        File file = folder.newFile("temp.cbh");
        FileOutputStream fos = new FileOutputStream(file);
        byte[] buf = new byte[0x1000];
        while (true) {
            int r = stream.read(buf);
            if (r == -1) {
                break;
            }
            fos.write(buf, 0, r);
        }
        fos.close();
        return file;
    }

    @Test
    public void openGameHeaderStorage() throws IOException {
        PersistentGameHeaderStorage storage = new PersistentGameHeaderStorage(gameHeaderFile, new GameHeaderBase());
        assertEquals(20, storage.getMetadata().getNextGameId());
        storage.close();
    }

    @Test
    public void getGameHeader() throws IOException {
        PersistentGameHeaderStorage storage = new PersistentGameHeaderStorage(gameHeaderFile, new GameHeaderBase());
        GameHeader header = storage.get(7);
        assertEquals(7, header.getId());
        storage.close();
    }

    @Test
    public void getGameHeaderRange() throws IOException {
        PersistentGameHeaderStorage storage = new PersistentGameHeaderStorage(gameHeaderFile, new GameHeaderBase());
        List<GameHeader> headers = storage.getRange(5, 12);
        assertEquals(7, headers.size());
        for (int i = 0; i < headers.size(); i++) {
            GameHeader header = headers.get(i);
            assertEquals(5 + i, header.getId());
        }
        storage.close();
    }

    @Test
    public void getGameHeaderRangeTooFar() throws IOException {
        PersistentGameHeaderStorage storage = new PersistentGameHeaderStorage(gameHeaderFile, new GameHeaderBase());
        List<GameHeader> headers = storage.getRange(10, 100);
        assertEquals(10, headers.size());
        for (int i = 0; i < headers.size(); i++) {
            GameHeader header = headers.get(i);
            assertEquals(10 + i, header.getId());
        }
        storage.close();
    }

    @Test
    public void createStorage() throws IOException {
        File file = folder.newFile("newbase.cbh");
        file.delete(); // Need to delete it first so we can create it
        PersistentGameHeaderStorage.createEmptyStorage(file, GameHeaderBase.emptyMetadata());
        GameHeaderStorageBase storage = PersistentGameHeaderStorage.open(file, new GameHeaderBase());
        assertEquals(1, storage.getMetadata().getNextGameId());
        assertNull(storage.get(1));
        storage.close();
    }

    @Test
    public void putGameHeaderReplaceExistingGame() throws IOException {
        long lengthBefore = gameHeaderFile.length();
        PersistentGameHeaderStorage storage = new PersistentGameHeaderStorage(gameHeaderFile, new GameHeaderBase());
        assertEquals(20, storage.getMetadata().getNextGameId());
        GameHeader gameHeader = GameHeader.defaultBuilder().whitePlayerId(5).blackPlayerId(8)
                .id(10).build();
        storage.put(gameHeader);
        storage.close();
        long lengthAfter = gameHeaderFile.length();
        assertEquals(lengthBefore, lengthAfter);
    }

    @Test
    public void putGameHeaderAtTheEnd() throws IOException {
        long lengthBefore = gameHeaderFile.length();
        PersistentGameHeaderStorage storage = new PersistentGameHeaderStorage(gameHeaderFile, new GameHeaderBase());
        assertEquals(20, storage.getMetadata().getNextGameId());
        GameHeader gameHeader = GameHeader.defaultBuilder().whitePlayerId(5).blackPlayerId(8)
                .id(20).build();
        storage.put(gameHeader);
        storage.close();
        long lengthAfter = gameHeaderFile.length();
        assertEquals(lengthBefore + 46, lengthAfter);
    }

    @Test(expected = IllegalArgumentException.class)
    public void putGameHeaderOutsideBounds() throws IOException {
        PersistentGameHeaderStorage storage = new PersistentGameHeaderStorage(gameHeaderFile, new GameHeaderBase());
        assertEquals(20, storage.getMetadata().getNextGameId());
        GameHeader gameHeader = GameHeader.defaultBuilder().whitePlayerId(5).blackPlayerId(8)
                .id(21).build();
        storage.put(gameHeader);
        storage.close();
    }

    @Test
    public void adjustGameHeaders() throws IOException {
        PersistentGameHeaderStorage storage = new PersistentGameHeaderStorage(gameHeaderFile, new GameHeaderBase());

        int n = storage.getMetadata().getNextGameId();

        int[] oldOffsets = new int[n];

        for (int i = 1; i < n; i++) {
            GameHeader gameHeader = storage.get(i);
            oldOffsets[i] = gameHeader.getMovesOffset();
        }

        int idChanged = 7, deltaSize = 170;
        storage.adjustMovesOffset(idChanged, oldOffsets[idChanged], deltaSize);

        for (int i = 1; i < n; i++) {
            GameHeader gameHeader = storage.get(i);
            int diff = gameHeader.getMovesOffset() - oldOffsets[i];
            int expected = i <= idChanged ? 0 : deltaSize;
            assertEquals("Game " + i + " should have diff " + expected, expected, diff);
        }

        storage.close();
    }
}
