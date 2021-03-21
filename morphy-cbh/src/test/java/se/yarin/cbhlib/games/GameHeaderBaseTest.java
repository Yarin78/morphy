package se.yarin.cbhlib.games;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import se.yarin.cbhlib.ResourceLoader;
import se.yarin.util.ByteBufferUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class GameHeaderBaseTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File gameHeaderFile;

    @Before
    public void setupEntityTest() throws IOException {
        gameHeaderFile = ResourceLoader.materializeStream(
                this.getClass().getResourceAsStream("cbhlib_test.cbh"),
                folder.newFile("temp.cbh"));
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
    public void testIterable() throws IOException {
        GameHeaderBase base = GameHeaderBase.open(gameHeaderFile);
        Iterator<GameHeader> iterator = base.iterable().iterator();
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
        Iterator<GameHeader> iterator = base.iterable().iterator();
        iterator.next();
        iterator.next();
        base.add(GameHeader.defaultBuilder().build());
        iterator.next();
    }

    @Test
    public void testSerializedGameHeaderFilter() throws IOException {
        File manyHeaderFile = ResourceLoader.materializeStream(
                this.getClass().getResourceAsStream("many_headers.cbh"),
                folder.newFile("many.cbh"));
        GameHeaderBase headerBase = GameHeaderBase.open(manyHeaderFile);
        // Create an iterator that will use the raw level filter to pick a handful of headers
        // The sparsity of this will cause some batches in the internal iterator to be empty
        List<Integer> lookupIds = Arrays.asList(1024, 5191, 5192, 5195, 5823, 9015);
        Stream<GameHeader> stream = headerBase.stream(1, serializedGameHeader -> {
            int id = ByteBufferUtil.getUnsigned24BitB(serializedGameHeader, 9); // id of white player offset
            return lookupIds.contains(id);
        });
        List<Integer> found = stream.map(GameHeader::getId).collect(Collectors.toList());
        assertEquals(lookupIds, found);
    }

    public static void main(String[] args) throws IOException {
        GameHeaderBase headers = GameHeaderBase.create(new File("many_headers.cbh"));
        for (int i = 0; i < 15000; i++) {
            GameHeader.GameHeaderBuilder builder = GameHeader.defaultBuilder();
            builder.whitePlayerId(i+1);
            builder.blackPlayerId(i+2);
            headers.add(builder.build());
        }
        headers.close();
    }

}
