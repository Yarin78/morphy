package se.yarin.cbhlib.games;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class InMemoryExtendedGameHeaderStorage extends ExtendedGameHeaderStorageBase {
    private static final Logger log = LoggerFactory.getLogger(InMemoryExtendedGameHeaderStorage.class);

    private TreeMap<Integer, ExtendedGameHeader> gameHeaders = new TreeMap<>();

    @Getter
    private int version = 0;

    InMemoryExtendedGameHeaderStorage() {
        super(ExtendedGameHeaderBase.emptyMetadata());
    }

    @Override
    ExtendedGameHeader get(int id) {
        return gameHeaders.get(id);
    }

    @Override
    List<ExtendedGameHeader> getRange(int startId, int endId) {
        if (startId < 1) throw new IllegalArgumentException("startId must be 1 or greater");
        ArrayList<ExtendedGameHeader> result = new ArrayList<>(endId - startId);
        for (int i = startId; i < endId; i++) {
            ExtendedGameHeader header = get(i);
            if (header == null) break;
            result.add(header);
        }
        return result;
    }

    @Override
    void put(ExtendedGameHeader gameHeader) {
        int gameId = gameHeader.getId();
        if (gameId < 1 || gameId > getMetadata().getNumHeaders() + 1) {
            throw new IllegalArgumentException(String.format("gameId outside range (was %d, numHeader is %d)", gameId, getMetadata().getNumHeaders()));
        }
        gameHeaders.put(gameId, gameHeader);
        version++;
    }
}
