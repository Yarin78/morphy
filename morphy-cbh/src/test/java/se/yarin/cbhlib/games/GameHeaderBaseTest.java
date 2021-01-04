package se.yarin.cbhlib.games;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.GameHeaderBase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;

public class GameHeaderBaseTest {

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
    public void createInMemoryBase() {
        GameHeaderBase base = new GameHeaderBase();
        assertEquals(1, base.getNextGameId());
    }

    @Test
    public void createBase() throws IOException {
        File file = folder.newFile("newbase.cbh");
        file.delete();
        GameHeaderBase base = GameHeaderBase.create(file);
        assertEquals(0, base.size());
        assertEquals(1, base.getNextGameId());
        base.close();
    }

    @Test
    public void openFromDiskInMemory() throws IOException {
        GameHeaderBase base = GameHeaderBase.openInMemory(gameHeaderFile);
        assertEquals(19, base.size());
        base.close();
    }

    @Test
    public void testAddGameHeader() throws IOException {
        GameHeaderBase base = GameHeaderBase.openInMemory(gameHeaderFile);
        int oldSize = base.size();

        GameHeader game1 = GameHeader.defaultBuilder().build();
        assertEquals(oldSize + 1, base.add(game1).getId());
        assertEquals(oldSize + 1, base.size());

        GameHeader game2 = GameHeader.defaultBuilder().id(5).build();
        assertEquals(oldSize + 2, base.add(game2).getId());
        assertEquals(oldSize + 2, base.size());

        base.close();
    }

    @Test
    public void testUpdateGameHeader() throws IOException {
        GameHeaderBase base = GameHeaderBase.openInMemory(gameHeaderFile);
        int oldSize = base.size();

        GameHeader oldGame = base.getGameHeader(3);
        assertEquals(4, oldGame.getWhitePlayerId());
        assertEquals(1, oldGame.getBlackPlayerId());

        GameHeader newGame = GameHeader.defaultBuilder().whitePlayerId(2).blackPlayerId(3).build();
        base.update(3, newGame);

        newGame = base.getGameHeader(3);
        assertEquals(2, newGame.getWhitePlayerId());
        assertEquals(3, newGame.getBlackPlayerId());
        assertEquals(oldSize, base.size());

        base.close();
    }

    @Test
    public void testIterator() throws IOException {
        GameHeaderBase base = GameHeaderBase.open(gameHeaderFile);
        Iterator<GameHeader> iterator = base.iterator();
        int id = 0;
        while (iterator.hasNext()) {
            assertEquals(++id, iterator.next().getId());
        }
        assertEquals(19, id);
        base.close();
    }

    @Test(expected = IllegalStateException.class)
    public void testIteratorWhileUpdating() throws IOException {
        GameHeaderBase base = GameHeaderBase.open(gameHeaderFile);
        Iterator<GameHeader> iterator = base.iterator();
        iterator.next();
        iterator.next();
        base.add(GameHeader.defaultBuilder().build());
        iterator.next();
    }

}
