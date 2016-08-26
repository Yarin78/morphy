package se.yarin.cbhlib;

import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class InMemoryGameHeaderStorage extends GameHeaderStorageBase {
    private static final Logger log = LoggerFactory.getLogger(InMemoryGameHeaderStorage.class);

    private TreeMap<Integer, GameHeader> gameHeaders = new TreeMap<>();

    @Getter
    private int version = 0;

    InMemoryGameHeaderStorage(@NonNull GameHeaderStorageMetadata metadata) {
        super(metadata);
    }

    @Override
    GameHeader get(int id) throws IOException {
        return gameHeaders.get(id);
    }

    @Override
    List<GameHeader> getRange(int startId, int endId) throws IOException {
        if (startId < 1) throw new IllegalArgumentException("startId must be 1 or greater");
        ArrayList<GameHeader> result = new ArrayList<>(endId - startId);
        for (int i = startId; i < endId; i++) {
            GameHeader header = get(i);
            if (header == null) break;
            result.add(header);
        }
        return result;
    }

    @Override
    void put(GameHeader gameHeader) throws IOException {
        int gameId = gameHeader.getId();
        if (gameId < 1 || gameId > getMetadata().getNextGameId()) {
            throw new IllegalArgumentException(String.format("gameId outside range (was %d, nextGameId is %d)", gameId, getMetadata().getNextGameId()));
        }
        gameHeaders.put(gameId, gameHeader);
        version++;
    }
}
