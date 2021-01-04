package se.yarin.cbhlib.games;

import org.junit.Test;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.cbhlib.games.InMemoryGameHeaderStorage;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class InMemoryGameHeaderStorageTest {
    @Test
    public void adjustGameHeaders() throws IOException {
        InMemoryGameHeaderStorage storage = new InMemoryGameHeaderStorage();

        storage.put(GameHeader.defaultBuilder().id(1).movesOffset(32).build());
        storage.getMetadata().setNextGameId(2);
        storage.put(GameHeader.defaultBuilder().id(2).movesOffset(80).build());
        storage.getMetadata().setNextGameId(3);
        storage.put(GameHeader.defaultBuilder().id(3).movesOffset(160).build());
        storage.getMetadata().setNextGameId(4);
        storage.put(GameHeader.defaultBuilder().id(4).movesOffset(200).build());
        storage.getMetadata().setNextGameId(5);

        storage.adjustMovesOffset(2, 80, 30);

        assertEquals(32, storage.get(1).getMovesOffset());
        assertEquals(80, storage.get(2).getMovesOffset());
        assertEquals(190, storage.get(3).getMovesOffset());
        assertEquals(230, storage.get(4).getMovesOffset());
    }
}
