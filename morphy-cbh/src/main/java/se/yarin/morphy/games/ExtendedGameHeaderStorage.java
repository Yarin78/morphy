package se.yarin.morphy.games;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.DatabaseContext;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.exceptions.MorphyException;
import se.yarin.morphy.exceptions.MorphyIOException;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.exceptions.MorphyNotSupportedException;
import se.yarin.morphy.storage.*;
import se.yarin.morphy.util.CBUtil;
import se.yarin.util.ByteBufferUtil;

import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.file.OpenOption;
import java.util.List;
import java.util.Set;

import static java.nio.file.StandardOpenOption.*;

/**
 * Represents the .cbj file containing additional GameHeader information.
 * This file may be missing in very old databases.
 * There are also multiple versions possible, with different sized records.
 */
public class ExtendedGameHeaderStorage implements ItemStorageSerializer<ExtendedGameHeaderStorage.ExtProlog, ExtendedGameHeader> {
    private static final Logger log = LoggerFactory.getLogger(ExtendedGameHeaderStorage.class);

    private final @NotNull ItemStorage<ExtendedGameHeaderStorage.ExtProlog, ExtendedGameHeader> storage;
    private final @NotNull DatabaseContext context;

    public ExtendedGameHeaderStorage() {
        this(null);
    }

    public ExtendedGameHeaderStorage(@Nullable DatabaseContext context) {
        this(new InMemoryItemStorage<>(ExtProlog.empty(), null, true), context);
    }

    private ExtendedGameHeaderStorage(@NotNull ItemStorage<ExtProlog, ExtendedGameHeader> storage, @Nullable DatabaseContext context) {
        this.storage = storage;
        this.context = context == null ? new DatabaseContext() : context;
    }

    private ExtendedGameHeaderStorage(@NotNull File file, @NotNull Set<OpenOption> options, @Nullable DatabaseContext context) throws IOException {
        if (!CBUtil.extension(file).equals(".cbj")) {
            throw new IllegalArgumentException("The file extension of an extended GameHeader storage must be .cbj");
        }
        this.storage = new FileItemStorage<>(file, this, ExtProlog.empty(), options);
        this.context = context == null ? new DatabaseContext() : context;

        if (options.contains(WRITE)) {
            if (storage.getHeader().version() < ExtProlog.DEFAULT_VERSION) {
                throw new MorphyNotSupportedException("Old extended game header storage version; upgrade needed but not yet supported.");
            }
            if (storage.getHeader().version() > ExtProlog.DEFAULT_VERSION) {
                throw new MorphyNotSupportedException("Newer unsupported extended game header storage format; writing not possible.");
            }
            if (storage.getHeader().serializedItemSize() != ExtProlog.DEFAULT_SERIALIZED_ITEM_SIZE) {
                // This shouldn't happen because of the version checks above, so this is mostly a sanity check
                // to ensure that same version doesn't have different record sizes.
                throw new MorphyNotSupportedException(String.format("Size of extended game header record was %d but expected %d; writing not possible.",
                        storage.getHeader().serializedItemSize(), ExtProlog.DEFAULT_SERIALIZED_ITEM_SIZE));
            }
        }
    }

    public static ExtendedGameHeaderStorage create(@NotNull File file, @Nullable DatabaseContext context)
            throws IOException, MorphyInvalidDataException {
        return new ExtendedGameHeaderStorage(file, Set.of(READ, WRITE, CREATE_NEW), context);
    }

    public static ExtendedGameHeaderStorage open(@NotNull File file, @Nullable DatabaseContext context) throws IOException {
        return open(file, DatabaseMode.READ_WRITE, context);
    }

    public static ExtendedGameHeaderStorage open(@NotNull File file, @NotNull DatabaseMode mode, @Nullable DatabaseContext context) throws IOException {
        if (mode == DatabaseMode.IN_MEMORY) {
            ExtendedGameHeaderStorage source = open(file, DatabaseMode.READ_ONLY, context);
            ExtendedGameHeaderStorage target = new ExtendedGameHeaderStorage(context);
            source.copyGameHeaders(target);
            return target;
        }
        return new ExtendedGameHeaderStorage(file, mode.openOptions(), context);
    }

    public DatabaseContext context() {
        return context;
    }

    static ExtProlog peekProlog(@NotNull File file) throws IOException {
        ExtendedGameHeaderStorage storage = open(file, DatabaseMode.READ_ONLY, null);
        ExtProlog prolog = storage.storage.getHeader();
        storage.close();
        return prolog;
    }

    public ExtProlog prolog() {
        return storage.getHeader();
    }

    int getStorageVersion() {
        return storage.getHeader().version();
    }

    /**
     * Gets an extended game header from the base
     * @param gameId the id of the game
     * @return an extended game header
     * @throws IllegalArgumentException if the gameId is invalid
     */
    public @NotNull ExtendedGameHeader get(int gameId) {
        try {
            return storage.getItem(gameId);
        } catch (MorphyIOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Attempts to repair/upgrade an extended game header storage by
     *   - create a new storage (if missing entirely)
     *   - upgrade an old version of the storage to a new one
     *   - pad missing entries
     * The cbh file is needed for the upgrade since empty items need offset data from the cbh file
     */
    public static void upgrade(@NotNull File cbhFile) throws IOException {
        if (!CBUtil.extension(cbhFile).equals(".cbh")) {
            throw new IllegalArgumentException("The extension should be .cbh");
        }
        if (!cbhFile.exists()) {
            // No need to upgrade if the main game header index doesn't even exist (e.g. when creating a new database)
            return;
        }

        DatabaseContext context = new DatabaseContext();
        GameHeaderIndex index = GameHeaderIndex.open(cbhFile, DatabaseMode.READ_ONLY, context);
        File cbjFile = CBUtil.fileWithExtension(cbhFile, ".cbj");
        if (!cbjFile.exists()) {
            log.info("Creating missing cbj file");
            ExtendedGameHeaderStorage extendedStorage = ExtendedGameHeaderStorage.create(cbjFile, null);
            for (int i = 1; i <= index.count(); i++) {
                GameHeader gameHeader = index.getGameHeader(i);
                extendedStorage.storage.putItem(i, ExtendedGameHeader.empty(gameHeader));
            }
            extendedStorage.storage.putHeader(ExtProlog.withCount(index.count()));
            extendedStorage.close();
        } else {
            ExtProlog currentProlog = peekProlog(cbjFile);
            if (currentProlog.version() < ExtProlog.DEFAULT_VERSION || currentProlog.serializedItemSize() < ExtProlog.DEFAULT_SERIALIZED_ITEM_SIZE) {
                log.info(String.format("Upgrading extended header storage from version %d (item size %d) to version %d (item size %d)",
                        currentProlog.version(), currentProlog.serializedItemSize(),
                        ExtProlog.DEFAULT_VERSION, ExtProlog.DEFAULT_SERIALIZED_ITEM_SIZE));

                ExtendedGameHeaderStorage extendedStorage = ExtendedGameHeaderStorage.open(cbjFile, DatabaseMode.READ_ONLY, null);
                File upgradedStorageFile = File.createTempFile(CBUtil.baseName(cbhFile), ".cbj");
                upgradedStorageFile.delete();
                ExtendedGameHeaderStorage upgradedStorage = ExtendedGameHeaderStorage.create(upgradedStorageFile, null);
                for (int i = 1; i <= extendedStorage.count(); i++) {
                    ExtendedGameHeader extHeader = extendedStorage.get(i);
                    upgradedStorage.storage.putItem(i, extHeader);
                }
                upgradedStorage.storage.putHeader(ExtProlog.withCount(extendedStorage.count()));
                upgradedStorage.close();
                extendedStorage.close();
                cbjFile.delete();
                upgradedStorageFile.renameTo(cbjFile);

                currentProlog = peekProlog(cbjFile);
            }

            if (currentProlog.version() < ExtProlog.DEFAULT_VERSION || currentProlog.serializedItemSize() < ExtProlog.DEFAULT_SERIALIZED_ITEM_SIZE) {
                throw new MorphyException("Upgrade of extended storage failed!? Still at the wrong version");
            }

            if (currentProlog.numHeaders() < index.count()) {
                ExtendedGameHeaderStorage extendedStorage = ExtendedGameHeaderStorage.open(cbjFile, DatabaseMode.READ_WRITE, null);

                log.warn("Extended storage contains fewer entries than expected; padding it");
                for (int i = extendedStorage.count() + 1; i <= index.count(); i++) {
                    GameHeader gameHeader = index.getGameHeader(i);
                    extendedStorage.storage.putItem(i, ExtendedGameHeader.empty(gameHeader));
                }
                extendedStorage.storage.putHeader(ExtProlog.withCount(index.count()));
                extendedStorage.close();
            }
        }
        index.close();
    }

    /**
     * Gets the number of game headers in the index
     * @return the number of game headers
     */
    public int count() {
        return storage.getHeader().numHeaders();
    }

    public void put(int gameId, ExtendedGameHeader extendedGameHeader) {
        storage.putItem(gameId, extendedGameHeader);
        if (gameId > count()) {
            storage.putHeader(ExtProlog.withCount(gameId));
        }
    }

    /**
     * Gets a list of all extended game headers in the storage.
     * @return a list of all ExtendedGameHeaders
     */
    public @NotNull List<ExtendedGameHeader> getAll() {
        return getRange(1, count() + 1);
    }

    /**
     * Gets a list of all extended game headers between startId (inclusive) and endId (exclusive).
     * The filter is applied on the serialized data, making this operation fast.
     * @param filter the filter, or null to match all extended game headers
     * @return a list of extended game headers. If a header doesn't match the filter, that position
     * in the list will have a null value. This is to allow easy zip join with
     * {@link GameHeaderIndex#getFiltered(ItemStorageFilter)}
     */
    public @NotNull List<ExtendedGameHeader> getFiltered(@Nullable ItemStorageFilter<ExtendedGameHeader> filter) {
        return getRange(1, count() + 1, filter);
    }

    /**
     * Gets a list of all extended game headers between startId (inclusive) and endId (exclusive)
     * @param startId the id of first game header (inclusive)
     * @param endId the id of the last game header (exclusive)
     * @return a list of extended game headers
     */
    public @NotNull List<ExtendedGameHeader> getRange(int startId, int endId) {
        return getRange(startId, endId, null);
    }

    /**
     * Gets a list of all extended game headers between startId (inclusive) and endId (exclusive)
     * @param startId the id of first game header (inclusive)
     * @param endId the id of the last game header (exclusive)
     * @param filter the filter, or null to match all extended game headers
     * @return a list of extended game headers. If a filter is specified and a header doesn't match, that position
     * in the list will have a null value.
     */
    public @NotNull List<ExtendedGameHeader> getRange(int startId, int endId, @Nullable ItemStorageFilter<ExtendedGameHeader> filter) {
        if (endId < startId) {
            throw new IllegalArgumentException(String.format("endId can't be less than startId (%d < %d)", endId, startId));
        }
        return storage.getItems(startId, endId - startId, filter);
    }

    public void copyGameHeaders(ExtendedGameHeaderStorage target) {
        if (target.count() != 0) {
            // Since this is a low-level copy, things will not work if there already are games in the target storage
            throw new IllegalStateException("Target storage must be empty");
        }
        for (int i = 1; i <= count(); i++) {
            target.storage.putItem(i, storage.getItem(i));
        }
        target.storage.putHeader(storage.getHeader());
    }

    @Value.Immutable
    public static abstract class ExtProlog {
        public static final int DEFAULT_VERSION = 11;
        public static final int DEFAULT_SERIALIZED_ITEM_SIZE = 120;
        public static final int SERIALIZED_HEADER_SIZE = 32;

        // cbj file version
        public abstract int version();

        // Size of each extended game header
        public abstract int serializedItemSize();

        // Number of extended game headers in the file. May mismatch!?
        public abstract int numHeaders();

        public static ExtProlog withCount(int numHeaders) {
            return ImmutableExtProlog.builder()
                    .version(DEFAULT_VERSION)
                    .serializedItemSize(DEFAULT_SERIALIZED_ITEM_SIZE)
                    .numHeaders(numHeaders)
                    .build();
        }

        public static ExtProlog empty() {
            return withCount(0);
        }

    }
    @Override
    public int headerSize(@NotNull ExtProlog header) {
        return ExtProlog.SERIALIZED_HEADER_SIZE;
    }

    @Override
    public int serializedHeaderSize() {
        return ExtProlog.SERIALIZED_HEADER_SIZE;
    }

    @Override
    public long itemOffset(@NotNull ExtProlog prolog, int id) {
        // Games are 1-indexed
        return ExtProlog.SERIALIZED_HEADER_SIZE + (long) (id - 1) * prolog.serializedItemSize();
    }

    @Override
    public int itemSize(@NotNull ExtProlog header) {
        return header.serializedItemSize();
    }

    @Override
    public @NotNull ExtProlog deserializeHeader(@NotNull ByteBuffer buf) throws MorphyInvalidDataException {
        return ImmutableExtProlog.builder()
                .version(ByteBufferUtil.getIntL(buf))
                .serializedItemSize(ByteBufferUtil.getIntL(buf))
                .numHeaders(ByteBufferUtil.getIntL(buf))
                .build();
    }

    @Override
    public void serializeHeader(@NotNull ExtProlog prolog, @NotNull ByteBuffer buf) {
        ByteBufferUtil.putIntL(buf, prolog.version());
        ByteBufferUtil.putIntL(buf, prolog.serializedItemSize());
        ByteBufferUtil.putIntL(buf, prolog.numHeaders());
        buf.position(buf.position() + ExtProlog.SERIALIZED_HEADER_SIZE - 12);
    }

    @Override
    public @NotNull ExtendedGameHeader deserializeItem(int id, @NotNull ByteBuffer buf) {
        int bufSize = buf.limit() - buf.position();

        ImmutableExtendedGameHeader.Builder builder = ImmutableExtendedGameHeader.builder();
        try {
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

    @Override
    public void serializeItem(@NotNull ExtendedGameHeader item, @NotNull ByteBuffer buf) {
        assert this.storage.getHeader().serializedItemSize() >= ExtProlog.DEFAULT_SERIALIZED_ITEM_SIZE;

        ByteBufferUtil.putIntB(buf, item.whiteTeamId());
        ByteBufferUtil.putIntB(buf, item.blackTeamId());
        // End of version 1 (8 bytes)
        ByteBufferUtil.putIntB(buf, item.mediaOffset());

        ByteBufferUtil.putLongB(buf, item.annotationOffset());
        // End of version 3 (20 bytes)

        if (item.finalMaterial()) {
            ByteBufferUtil.putIntB(buf, FinalMaterial.encode(item.materialPlayer1()));
            ByteBufferUtil.putIntB(buf, FinalMaterial.encode(item.materialPlayer2()));
            ByteBufferUtil.putShortB(buf, FinalMaterial.encode(item.materialTotal()));
        } else {
            ByteBufferUtil.putIntB(buf, 0);
            ByteBufferUtil.putIntB(buf, 0);
            ByteBufferUtil.putShortB(buf, 0xFFFE);
        }
        // End of version 4 and 5 (30 bytes)

        ByteBufferUtil.putLongB(buf, item.movesOffset());
        // End of version 6 (38 bytes)

        item.whiteRatingType().serialize(buf);
        item.blackRatingType().serialize(buf);
        ByteBufferUtil.putIntB(buf, item.unknown1());
        // End of version 7 (74 bytes)
        ByteBufferUtil.putIntB(buf, item.unknown2());
        // End of version 8 (78 bytes)
        ByteBufferUtil.putShortB(buf, item.gameVersion());
        ByteBufferUtil.putLongB(buf, item.creationTimestamp());
        if (item.endgameInfo() != null) {
            item.endgameInfo().serialize(buf);
        } else {
            buf.put(new byte[20]);
        }
        ByteBufferUtil.putLongB(buf, item.lastChangedTimestamp());

        ByteBufferUtil.putIntB(buf, item.gameTagId());
        // End of version 11 (120 bytes)

        // If we're writing to a newer version of the file than we support, fill out with zeros
        for (int i = ExtProlog.DEFAULT_SERIALIZED_ITEM_SIZE; i < this.storage.getHeader().serializedItemSize(); i++) {
            buf.put((byte) 0);
        }
    }

    @Override
    public @NotNull ExtendedGameHeader emptyItem(int id) {
        return ExtendedGameHeader.empty(0, 0);
    }

    public void close() {
        storage.close();
    }
}
