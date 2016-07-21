package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.TreeMap;

public class InMemoryGameHeaderStorage extends GameHeaderStorageBase {
    private static final Logger log = LoggerFactory.getLogger(InMemoryGameHeaderStorage.class);

    private TreeMap<Integer, GameHeader> gameHeaders = new TreeMap<>();

    InMemoryGameHeaderStorage(@NonNull GameHeaderStorageMetadata metadata) {
        super(metadata);
    }

    @Override
    GameHeader get(int id) throws IOException {
        return gameHeaders.get(id);
    }

    @Override
    void put(GameHeader gameHeader) throws IOException {
        gameHeaders.put(gameHeader.getId(), gameHeader);
    }
}
