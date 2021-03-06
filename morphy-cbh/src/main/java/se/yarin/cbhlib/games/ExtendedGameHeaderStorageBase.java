package se.yarin.cbhlib.games;

import lombok.NonNull;
import se.yarin.cbhlib.exceptions.ChessBaseIOException;

import java.io.IOException;
import java.util.List;

abstract class ExtendedGameHeaderStorageBase {
    private ExtendedGameHeaderStorageMetadata metadata;

    ExtendedGameHeaderStorageBase(@NonNull ExtendedGameHeaderStorageMetadata metadata) {
        this.metadata = metadata;
    }

    ExtendedGameHeaderStorageMetadata getMetadata() {
        return this.metadata;
    }

    void setMetadata(ExtendedGameHeaderStorageMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * Gets an extended game header
     * @param id the id of the extended game header to get
     * @return an extended game header, or null if there is no extended game header with that id
     * @throws ChessBaseIOException if there was some IO error reading the extended game header
     */
    abstract ExtendedGameHeader get(int id);

    /**
     * Gets a range of extended game headers. The resulting list will contain fewer
     * than endId-startId headers if endId is larger than highest id in the database.
     * @param startId the id of the first extended game header to get
     * @param endId the id of the first extended game header NOT to get
     * @return a list of extended game headers between startId and endId in ascending order
     * @throws ChessBaseIOException if there was some IO error reading the extended game headers
     */
    abstract List<ExtendedGameHeader> getRange(int startId, int endId);

    /**
     * Puts an extended game header in the storage, either overwriting an existing extended game header
     * or adding it to the end. The id must be set and must be between
     * 1 and nextGameId, inclusive.
     * @param extendedGameHeader the extended game header to put
     * @throws ChessBaseIOException if there was some IO error putting the extended game header
     * @throws IllegalArgumentException is the id in extendedGameHeader is outside the valid range
     */
    abstract void put(ExtendedGameHeader extendedGameHeader);

    /**
     * Adjusts the moves offset of all extended game headers that have their move offsets
     * greater than the specified value.
     * @param startGameId the first gameId to consider
     * @param movesOffset a game is only affected if its moves offset is greater than this
     * @param insertedBytes the number of bytes to adjust with
     */
    abstract void adjustMovesOffset(int startGameId, long movesOffset, long insertedBytes);

    /**
     * Adjusts the annotation offset of all extended game headers that have their annotation offsets
     * greater than the specified value.
     * @param startGameId the first gameId to consider
     * @param annotationOffset a game is only affected if its annotation offset is greater than this
     * @param insertedBytes the number of bytes to adjust with
     */
    abstract void adjustAnnotationOffset(int startGameId, long annotationOffset, long insertedBytes);

    /**
     * The number of modifying operations to the storage since it was opened.
     * This field is not persisted.
     * @return the version
     */
    abstract int getVersion();

    void close() throws IOException { }
}
