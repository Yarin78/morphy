package se.yarin.cbhlib.games;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class InMemoryGameHeaderStorage extends GameHeaderStorageBase {
    private static final Logger log = LoggerFactory.getLogger(InMemoryGameHeaderStorage.class);

    private TreeMap<Integer, GameHeader> gameHeaders = new TreeMap<>();

    @Getter
    private int version = 0;

    InMemoryGameHeaderStorage() {
        super(GameHeaderBase.emptyMetadata());
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

    @Override
    void adjustMovesOffset(int startGameId, int movesOffset, int insertedBytes) throws IOException {
        List<Integer> gameIds = new ArrayList<>(gameHeaders.tailMap(startGameId).keySet());
        for (int gameId : gameIds) {
            GameHeader header = gameHeaders.get(gameId);
            if (header.getMovesOffset() > movesOffset) {
                GameHeader newHeader = header.toBuilder().movesOffset(header.getMovesOffset() + insertedBytes).build();
                gameHeaders.put(gameId, newHeader);
            }
        }
    }

    @Override
    void adjustAnnotationOffset(int startGameId, int annotationOffset, int insertedBytes) throws IOException {
        List<Integer> gameIds = new ArrayList<>(gameHeaders.tailMap(startGameId).keySet());
        for (int gameId : gameIds) {
            GameHeader header = gameHeaders.get(gameId);
            if (header.getAnnotationOffset() > annotationOffset) {
                GameHeader newHeader = header.toBuilder().annotationOffset(header.getAnnotationOffset() + insertedBytes).build();
                gameHeaders.put(gameId, newHeader);
            }
        }
    }
}
