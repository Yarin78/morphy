package se.yarin.cbhlib.games;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.util.ByteBufferUtil;

import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class ExtendedGameHeaderBase implements ExtendedGameHeaderSerializer {
    private static final Logger log = LoggerFactory.getLogger(ExtendedGameHeaderBase.class);

    private static final int DEFAULT_SERIALIZED_EXTENDED_GAME_HEADER_SIZE = 120;
    private static final int DEFAULT_VERSION = 11;

    private final ExtendedGameHeaderStorageBase storage;

    /**
     * Creates a new game header base that is initially empty.
     */
    public ExtendedGameHeaderBase() {
        this.storage = new InMemoryExtendedGameHeaderStorage();
    }

    private ExtendedGameHeaderBase(@NonNull ExtendedGameHeaderStorageBase storage) {
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
     * If the file doesn't exist, an empty extended header base is created.
     * @param file the initial data of the database
     * @return an in-memory extended game header base
     */
    public static ExtendedGameHeaderBase openInMemory(@NonNull File file) throws IOException {
        if (!file.exists()) {
            return new ExtendedGameHeaderBase();
        }
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
    public ExtendedGameHeader getExtendedGameHeader(int gameId) {
        return storage.get(gameId);
    }

    /**
     * Updates an existing game header
     * @param gameId the id of the extended game header to update
     * @param extendedGameHeader the new data of the game header
     * @return the saved gameHeader
     */
    public ExtendedGameHeader update(int gameId, @NonNull ExtendedGameHeader extendedGameHeader) {
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
    public ExtendedGameHeader add(@NonNull ExtendedGameHeader extendedGameHeader) {
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
     * Returns an Iterable over all game headers in the database
     * @return an iterable
     */
    public Iterable<ExtendedGameHeader> iterable() {
        return iterable(1);
    }

    /**
     * Returns an Iterable over all game headers in the database
     * @param gameId the id to start the iterable at (inclusive)
     * @return an iterable
     */
    public Iterable<ExtendedGameHeader> iterable(int gameId) {
        return () -> new ExtendedGameHeaderBase.DefaultIterator(gameId, null);
    }

    /**
     * Returns a stream over all game headers in the database
     * @return a stream
     */
    public Stream<ExtendedGameHeader> stream() {
        return stream(1, null);
    }

    /**
     * Returns a stream over all game headers in the database starting at the specified id
     * @param gameId the id to start the stream at (inclusive)
     * @return a stream
     */
    public Stream<ExtendedGameHeader> stream(int gameId) {
        return stream(gameId, null);
    }

    /**
     * Returns a stream over all extended game headers in the database starting at the specified id
     * @param gameId the id to start the stream at (inclusive)
     * @param filter a optional low level filter that _may_ filter out games at the ByteBuffer level.
     *               The filter has no effect if the data has already been deserialized, so a proper
     *               {@link se.yarin.cbhlib.games.search.SearchFilter} should be used as well.
     *               However, since it's much faster to filter things out at this level, it's a nice performance boost.
     * @return a stream
     */
    public Stream<ExtendedGameHeader> stream(int gameId, SerializedExtendedGameHeaderFilter filter) {
        if (filter != null && !(storage instanceof PersistentExtendedGameHeaderStorage)) {
            log.warn("A serialized ExtendedGameHeader filter was specified in iteration but the underlying storage doesn't support it");
        }
        Iterable<ExtendedGameHeader> iterable = () -> new ExtendedGameHeaderBase.DefaultIterator(gameId, filter);
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    public boolean canGetRaw() {
        return storage instanceof PersistentExtendedGameHeaderStorage;
    }

    /**
     * Gets the underlying raw data for an extended game header.
     * For debugging purposes only.
     * @param gameId the id of the game to get
     * @return a byte array containing the underlying data
     */
    public byte[] getRaw(int gameId) {
        if (!(storage instanceof PersistentExtendedGameHeaderStorage)) {
            throw new IllegalStateException("The underlying storage is not a persistent storage");
        }
        return ((PersistentExtendedGameHeaderStorage) storage).getRaw(gameId);
    }


    private class DefaultIterator implements Iterator<ExtendedGameHeader> {
        // TODO: This is almost duplicated with GameHeaderBase.DefaultIterator - simplify?
        private List<ExtendedGameHeader> batch = new ArrayList<>();
        private static final int BATCH_SIZE = 1000;
        private int batchPos, nextBatchStart;
        private final int version;
        private final SerializedExtendedGameHeaderFilter filter;

        private void getNextBatch() {
            int endId = Math.min(size() + 1, nextBatchStart + BATCH_SIZE);
            if (nextBatchStart >= endId) {
                batch = null;
            } else {
                if (storage instanceof PersistentExtendedGameHeaderStorage) {
                    batch = ((PersistentExtendedGameHeaderStorage) storage).getRange(nextBatchStart, endId, filter);
                } else {
                    batch = storage.getRange(nextBatchStart, endId);
                }
                nextBatchStart = endId;
            }
            batchPos = 0;
        }

        DefaultIterator(int startId, SerializedExtendedGameHeaderFilter filter) {
            version = storage.getVersion();
            this.filter = filter;
            nextBatchStart = startId;
            prefetchBatch();
        }

        private void prefetchBatch() {
            while (batch != null && batchPos == batch.size()) {
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
            ByteBufferUtil.putIntB(buf, FinalMaterial.encode(header.getMaterialPlayer1()));
            ByteBufferUtil.putIntB(buf, FinalMaterial.encode(header.getMaterialPlayer2()));
            ByteBufferUtil.putShortB(buf, FinalMaterial.encode(header.getMaterialTotal()));
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
        ByteBufferUtil.putShortB(buf, header.getGameVersion());
        ByteBufferUtil.putLongB(buf, header.getCreationTimestamp());
        if (header.getEndgameInfo() != null) {
            header.getEndgameInfo().serialize(buf);
        } else {
            buf.put(new byte[20]);
        }
        ByteBufferUtil.putLongB(buf, header.getLastChangedTimestamp());

        ByteBufferUtil.putIntB(buf, header.getGameTagId());
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
                    builder.materialPlayer1(FinalMaterial.decode(s3));
                    builder.materialPlayer2(FinalMaterial.decode(s4));
                    builder.materialTotal(FinalMaterial.decode(s5));
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
                builder.whiteRatingType(RatingType.unspecified());
                builder.blackRatingType(RatingType.unspecified());
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
                builder.creationTimestamp(creationTimestamp);

                int position = buf.position();
                builder.endgameInfo(EndgameInfo.deserialize(buf));
                buf.position(position + 20);

                builder.lastChangedTimestamp(ByteBufferUtil.getLongB(buf));
                int gameTagId = ByteBufferUtil.getIntB(buf);
                builder.gameTagId(gameTagId);
            } else {
                // TODO: Set reasonable defaults to timestamps
                builder.gameTagId(-1);
            }
        } catch (BufferUnderflowException e) {
            log.warn("Unexpected end of extended game header buffer", e);
        }

        return builder.build();
    }

    /**
     * Adjusts the moves offset of all extended game headers that have their move offsets
     * greater than the specified value.
     * @param startGameId the first gameId to consider
     * @param movesOffset a game is only affected if its moves offset is greater than this
     * @param insertedBytes the number of bytes to adjust with
     */
    public void adjustMovesOffset(int startGameId, long movesOffset, long insertedBytes) {
        storage.adjustMovesOffset(startGameId, movesOffset, insertedBytes);
    }

    /**
     * Adjusts the annotation offset of all extended game headers that have their annotation offsets
     * greater than the specified value.
     * @param startGameId the first gameId to consider
     * @param annotationOffset a game is only affected if its annotation offset is greater than this
     * @param insertedBytes the number of bytes to adjust with
     */
    public void adjustAnnotationOffset(int startGameId, long annotationOffset, long insertedBytes) {
        storage.adjustAnnotationOffset(startGameId, annotationOffset, insertedBytes);
    }

    @Override
    public int getSerializedExtendedGameHeaderLength() {
        return storage.getMetadata().getSerializedExtendedGameHeaderSize();
    }

    public void close() throws IOException {
        storage.close();
    }
}
