package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.entities.UncheckedEntityException;

import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class ExtendedGameHeaderBase implements ExtendedGameHeaderSerializer, Iterable<ExtendedGameHeader> {
    private static final Logger log = LoggerFactory.getLogger(ExtendedGameHeaderBase.class);

    private static final int DEFAULT_SERIALIZED_EXTENDED_GAME_HEADER_SIZE = 120;
    private static final int DEFAULT_VERSION = 11;

    private ExtendedGameHeaderStorageBase storage;

    /**
     * Creates a new game header base that is initially empty.
     */
    public ExtendedGameHeaderBase() {
        this.storage = new InMemoryExtendedGameHeaderStorage();
    }

    private ExtendedGameHeaderBase(@NonNull ExtendedGameHeaderStorageBase storage) throws IOException {
        this.storage = storage;
    }

    static ExtendedGameHeaderStorageMetadata emptyMetadata() {
        ExtendedGameHeaderStorageMetadata metadata = new ExtendedGameHeaderStorageMetadata();
        metadata.setVersion(DEFAULT_VERSION);
        metadata.setSerializedExtendedGameHeaderSize(DEFAULT_SERIALIZED_EXTENDED_GAME_HEADER_SIZE);
        metadata.setNumHeaders(0);
        return metadata;
    }

    /**
     * Creates an in-memory extended game header base initially populated with the data from the file
     * @param file the initial data of the database
     * @return an in-memory extended game header base
     */
    public static ExtendedGameHeaderBase openInMemory(@NonNull File file) throws IOException {
        ExtendedGameHeaderBase source = open(file);
        ExtendedGameHeaderBase target = new ExtendedGameHeaderBase();
        for (int i = 0; i < source.size(); i++) {
            ExtendedGameHeader extendedGameHeader = source.getExtendedGameHeader(i + 1);
            target.add(extendedGameHeader);
        }
        return target;
    }

    /**
     * Opens an extended game header database from disk
     * @param file the extended game header databases to open
     * @return the opened extended game header database
     * @throws IOException if something went wrong when opening the database
     */
    public static ExtendedGameHeaderBase open(@NonNull File file) throws IOException {
        return new ExtendedGameHeaderBase(PersistentExtendedGameHeaderStorage.open(file, new ExtendedGameHeaderBase()));
    }

    /**
     * Creates a new extended game header database on disk.
     * If the target file already exists, an {@link IOException} is thrown.
     * @param file the file to create
     * @return the opened extended game header database
     * @throws IOException if something went wrong when creating the database
     */
    public static ExtendedGameHeaderBase create(@NonNull File file) throws IOException {
        PersistentExtendedGameHeaderStorage.createEmptyStorage(file, emptyMetadata());
        return ExtendedGameHeaderBase.open(file);
    }

    /**
     * Gets the number of extended game headers
     * @return the number of extended game headers
     */
    public int size() {
        // TODO: rename to getCount() to make it consistent with other bases?
        return storage.getMetadata().getNumHeaders();
    }

    /**
     * Gets the schema version of the extended header base
     * @return a schema version number
     */
    public int version() {
        return storage.getMetadata().getVersion();
    }

    /**
     * Gets an extended game header from the base
     * @param gameId the id of the game
     * @return an extended game header, or null if there was no extended game header with the specified id
     */
    public ExtendedGameHeader getExtendedGameHeader(int gameId) throws IOException {
        return storage.get(gameId);
    }

    /**
     * Updates an existing game header
     * @param gameId the id of the extended game header to update
     * @param extendedGameHeader the new data of the game header
     * @return the saved gameHeader
     */
    public ExtendedGameHeader update(int gameId, @NonNull ExtendedGameHeader extendedGameHeader) throws IOException {
        // The id may not be set in gameHeader, in which case we need to do this now
        if (extendedGameHeader.getId() != gameId) {
            extendedGameHeader = extendedGameHeader.toBuilder().id(gameId).build();
        }
        storage.put(extendedGameHeader);
        return extendedGameHeader;
    }

    /**
     * Adds a new extended game header to the base. The id field in extendedGameHeader will be ignored.
     * @param extendedGameHeader the extended game header to add
     * @return the id that the extended game header received
     */
    public ExtendedGameHeader add(@NonNull ExtendedGameHeader extendedGameHeader) throws IOException {
        // TODO: Extended game headers should probably not be added without specifying id
        int nextGameId = storage.getMetadata().getNumHeaders() + 1;
        extendedGameHeader = extendedGameHeader.toBuilder().id(nextGameId).build();
        storage.put(extendedGameHeader);

        ExtendedGameHeaderStorageMetadata metadata = storage.getMetadata();
        metadata.setNumHeaders(nextGameId);
        storage.setMetadata(metadata);
        return extendedGameHeader;
    }


    /**
     * Gets an iterator over all game headers in the database
     * @return an iterator
     */
    public Iterator<ExtendedGameHeader> iterator() {
        return new DefaultIterator(1);
    }

    /**
     * Gets an iterator over all game headers in the database starting at the specified id
     * @param gameId the id to start the iteration at (inclusive)
     * @return an iterator
     */
    public Iterator<ExtendedGameHeader> iterator(int gameId) {
        return new DefaultIterator(gameId);
    }


    private class DefaultIterator implements Iterator<ExtendedGameHeader> {
        private List<ExtendedGameHeader> batch = new ArrayList<>();
        private int batchPos, nextBatchStart = 0, batchSize = 1000;
        private final int version;

        private void getNextBatch() {
            int endId = Math.min(size() + 1, nextBatchStart + batchSize);
            if (nextBatchStart >= endId) {
                batch = null;
            } else {
                try {
                    batch = storage.getRange(nextBatchStart, endId);
                } catch (IOException e) {
                    throw new UncheckedEntityException("An IO error when iterating game headers", e);
                }
                nextBatchStart = endId;
            }
            batchPos = 0;
        }

        DefaultIterator(int startId) {
            version = storage.getVersion();
            nextBatchStart = startId;
            prefetchBatch();
        }

        private void prefetchBatch() {
            if (batch == null || batchPos == batch.size()) {
                getNextBatch();
            }
        }

        @Override
        public boolean hasNext() {
            if (version != storage.getVersion()) {
                throw new IllegalStateException("The storage has changed since the iterator was created");
            }
            return batch != null;
        }

        @Override
        public ExtendedGameHeader next() {
            if (!hasNext()) {
                throw new NoSuchElementException("End of game header iteration reached");
            }
            ExtendedGameHeader extendedGameHeader = batch.get(batchPos++);
            prefetchBatch();
            return extendedGameHeader;
        }
    }

    @Override
    public ByteBuffer serialize(ExtendedGameHeader header) {
        ByteBuffer buf = ByteBuffer.allocate(DEFAULT_SERIALIZED_EXTENDED_GAME_HEADER_SIZE);

        ByteBufferUtil.putIntB(buf, header.getWhiteTeamId());
        ByteBufferUtil.putIntB(buf, header.getBlackTeamId());
        // End of version 1 (8 bytes)
        ByteBufferUtil.putIntB(buf, header.getMediaOffset());

        ByteBufferUtil.putLongB(buf, header.getAnnotationOffset());
        // End of version 3 (20 bytes)

        if (header.isFinalMaterial()) {
            ByteBufferUtil.putIntB(buf, CBUtil.encodeFinalMaterial(header.getMaterialPlayer1()));
            ByteBufferUtil.putIntB(buf, CBUtil.encodeFinalMaterial(header.getMaterialPlayer2()));
            ByteBufferUtil.putShortB(buf, CBUtil.encodeFinalMaterial(header.getMaterialTotal()));
        } else {
            ByteBufferUtil.putIntB(buf, 0);
            ByteBufferUtil.putIntB(buf, 0);
            ByteBufferUtil.putShortB(buf, 0xFFFE);
        }
        // End of version 4 and 5 (30 bytes)

        ByteBufferUtil.putLongB(buf, header.getMovesOffset());
        // End of version 6 (38 bytes)

        header.getWhiteRatingType().serialize(buf);
        header.getBlackRatingType().serialize(buf);
        ByteBufferUtil.putIntB(buf, header.getUnknown1());
        // End of version 7 (74 bytes)
        ByteBufferUtil.putIntB(buf, header.getUnknown2());
        // End of version 8 (78 bytes)
        ByteBufferUtil.putIntB(buf, header.getGameVersion());
        ByteBufferUtil.putLongB(buf, header.getCreationTimestamp());
        header.getEndgame().serialize(buf);
        ByteBufferUtil.putLongB(buf, header.getLastChangedTimestamp());

        ByteBufferUtil.putIntB(buf, 0);
        // End of version 11 (120 bytes)

        assert buf.position() == buf.limit();
        // Cut the buffer to the actual number of bytes this instance supports
        buf.position(getSerializedExtendedGameHeaderLength());
        buf.flip();
        return buf;
    }

    @Override
    public ExtendedGameHeader deserialize(int gameId, ByteBuffer buf) {
        int bufSize = buf.limit() - buf.position();

        ExtendedGameHeader.ExtendedGameHeaderBuilder builder = ExtendedGameHeader.builder();
        try {
            builder.id(gameId);
            builder.whiteTeamId(ByteBufferUtil.getIntB(buf));
            builder.blackTeamId(ByteBufferUtil.getIntB(buf));
            if (bufSize >= 12) {
                builder.mediaOffset(ByteBufferUtil.getIntB(buf));
            }
            if (bufSize >= 20) {
                builder.annotationOffset(ByteBufferUtil.getLongB(buf));
            }
            if (bufSize >= 30) {
                int s1 = ByteBufferUtil.getSignedShortB(buf);
                int s3 = ByteBufferUtil.getUnsignedShortB(buf);
                int s2 = ByteBufferUtil.getSignedShortB(buf);
                int s4 = ByteBufferUtil.getUnsignedShortB(buf);
                int s5 = ByteBufferUtil.getUnsignedShortB(buf);

                // Some checking if these values are actually set
                if (s5 == 0xFFFE || (s3 == 0 && s4 ==0) || (s1 == -1 || s2 == -1)) {
                    builder.finalMaterial(false);
                } else {
                    builder.finalMaterial(true);
                    builder.materialPlayer1(CBUtil.decodeFinalMaterial(s3));
                    builder.materialPlayer2(CBUtil.decodeFinalMaterial(s4));
                    builder.materialTotal(CBUtil.decodeFinalMaterial(s5));
                }
            }
            if (bufSize >= 38) {
                builder.movesOffset(ByteBufferUtil.getLongB(buf));
            }
            if (bufSize >= 74) {
                builder.whiteRatingType(RatingType.deserialize(buf));
                builder.blackRatingType(RatingType.deserialize(buf));
                builder.unknown1(ByteBufferUtil.getIntB(buf)); // No idea what this is for
            } else {
                builder.whiteRatingType(RatingType.international(TournamentTimeControl.NORMAL));
                builder.blackRatingType(RatingType.international(TournamentTimeControl.NORMAL));
            }
            if (bufSize >= 78) {
                builder.unknown2(ByteBufferUtil.getIntB(buf)); // No idea what this is for
            }
            if (bufSize >= 120) {
                builder.gameVersion(ByteBufferUtil.getUnsignedShortB(buf));
                long creationTimestamp = ByteBufferUtil.getLongB(buf);
                if (creationTimestamp < 0 || creationTimestamp > 2970943488000L) {
                    // Invalid value - must be within 21st century!
                    creationTimestamp = 0;
                }
                int position = buf.position();
                builder.endgame(Endgame.deserialize(buf));
                buf.position(position + 20);

                builder.lastChangedTimestamp(ByteBufferUtil.getLongB(buf));

                // Not sure about this; it's always 0 when creationTimestamp exists
                // and none zero (usually -1) otherwise. Probably some check against dirty data.
                int valid = ByteBufferUtil.getIntB(buf);
                if (valid != 0) {
                    creationTimestamp = 0;
                }
                builder.creationTimestamp(creationTimestamp);
            } else {
                builder.endgame(new Endgame());
            }
        } catch (BufferUnderflowException e) {
            log.warn("Unexpected end of extended game header buffer", e);
        }

        return builder.build();
    }

    @Override
    public int getSerializedExtendedGameHeaderLength() {
        return storage.getMetadata().getSerializedExtendedGameHeaderSize();
    }

    public void close() throws IOException {
        storage.close();
    }
}
