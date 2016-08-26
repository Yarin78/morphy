package se.yarin.cbhlib;

import lombok.NonNull;

import java.io.IOException;
import java.util.List;

abstract class GameHeaderStorageBase {
    private GameHeaderStorageMetadata metadata;

    GameHeaderStorageBase(@NonNull GameHeaderStorageMetadata metadata) {
        this.metadata = metadata;
    }

    GameHeaderStorageMetadata getMetadata() {
        return this.metadata;
    }

    void setMetadata(GameHeaderStorageMetadata metadata) throws IOException {
        this.metadata = metadata;
    }

    /**
     * Gets a game header
     * @param id the id of the game header to get
     * @return a game header, or null if there is no game header with that id
     * @throws IOException if there was some IO error reading the game header
     */
    abstract GameHeader get(int id) throws IOException;

    /**
     * Gets a range of game headers. The resulting list will contain fewer
     * than endId-startId headers if endId is larger than highest id in the database.
     * @param startId the id of the first game header to get
     * @param endId the id of the first game header NOT to get
     * @return a list of game headers between startId and endId in ascending order
     * @throws IOException if there was some IO error reading the game headers
     */
    abstract List<GameHeader> getRange(int startId, int endId) throws IOException;

    /**
     * Puts a game header in the storage, either overwriting an existing game header
     * or adding it to the end. The id must be set and must be between
     * 1 and nextGameId, inclusive.
     * @param gameHeader the game header to put
     * @throws IOException if there was some IO error putting the game header
     * @throws IllegalArgumentException is the id in gameHeader is outside the valid range
     */
    abstract void put(GameHeader gameHeader) throws IOException;

    /**
     * The number of modifying operations to the storage since it was opened.
     * This field is not persisted.
     * @return the version
     */
    abstract int getVersion();

    void close() throws IOException { }
}
