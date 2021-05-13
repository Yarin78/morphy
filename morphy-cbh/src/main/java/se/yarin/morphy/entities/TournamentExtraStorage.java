package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.DatabaseContext;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.Instrumentation;
import se.yarin.morphy.util.CBUtil;
import se.yarin.util.ByteBufferUtil;
import se.yarin.chess.Date;
import se.yarin.morphy.exceptions.MorphyNotSupportedException;
import se.yarin.morphy.storage.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.nio.file.StandardOpenOption.*;

/**
 * Represents the .cbtt file containing additional Tournament information.
 * May contain fewer entries than the .cbt file as entries are only written if they contain any real data,
 * or if needed to fill up the space up until such an entry.
 */
public class TournamentExtraStorage implements ItemStorageSerializer<TournamentExtraHeader, TournamentExtra> {

    private static final Logger log = LoggerFactory.getLogger(TournamentExtraStorage.class);

    private final @NotNull ItemStorage<TournamentExtraHeader, TournamentExtra> storage;
    private final @NotNull DatabaseContext context;
    private final @NotNull Instrumentation.SerializationStats serializationStats;

    public TournamentExtraStorage() {
        this(null);
    }

    public TournamentExtraStorage(@Nullable DatabaseContext context) {
        this(new InMemoryItemStorage<>(TournamentExtraHeader.empty(), TournamentExtra.empty()), context);
    }

    public TournamentExtraStorage(@NotNull ItemStorage<TournamentExtraHeader, TournamentExtra> storage, @Nullable DatabaseContext context) {
        this.storage = storage;
        this.context = context == null ? new DatabaseContext() : context;
        this.serializationStats = this.context.instrumentation().serializationStats("TournamentExt");
    }

    protected TournamentExtraStorage(@NotNull File file, @NotNull Set<OpenOption> options, @Nullable DatabaseContext context) throws IOException {
        this.context = context == null ? new DatabaseContext() : context;
        this.storage = new FileItemStorage<>(file, this.context, this, TournamentExtraHeader.empty(), options);
        this.serializationStats = this.context.instrumentation().serializationStats("TournamentExt");

        if (options.contains(WRITE)) {
            if (storage.getHeader().version() < TournamentExtraHeader.DEFAULT_HEADER_VERSION) {
                throw new MorphyNotSupportedException(String.format("Old extra tournament storage version; upgrade needed but not yet supported (%d < %d)",
                        storage.getHeader().version(), TournamentExtraHeader.DEFAULT_HEADER_VERSION));
            }
            if (storage.getHeader().version() > TournamentExtraHeader.DEFAULT_HEADER_VERSION) {
                throw new MorphyNotSupportedException(String.format("Newer unsupported extra tournament storage format; writing not possible (%d > %d)",
                        storage.getHeader().version(), TournamentExtraHeader.DEFAULT_HEADER_VERSION));
            }
            if (storage.getHeader().recordSize() != TournamentExtraHeader.DEFAULT_RECORD_SIZE) {
                // This shouldn't happen because of the version checks above, so this is mostly a sanity check
                // to ensure that same version doesn't have different record sizes.
                throw new MorphyNotSupportedException(String.format("Size of extra tournament record was %d but expected %d; writing not possible.",
                        storage.getHeader().recordSize(), TournamentExtraHeader.DEFAULT_RECORD_SIZE));
            }
        }
    }

    public static TournamentExtraStorage create(@NotNull File file, @Nullable DatabaseContext context) throws IOException {
        return new TournamentExtraStorage(file, Set.of(READ, WRITE, CREATE_NEW), context);
    }

    public static TournamentExtraStorage open(@NotNull File file, @Nullable DatabaseContext context) throws IOException {
        return open(file, DatabaseMode.READ_WRITE, context);
    }

    public static TournamentExtraStorage open(@NotNull File file, @NotNull DatabaseMode mode, @Nullable DatabaseContext context) throws IOException {
        if (mode == DatabaseMode.IN_MEMORY) {
            TournamentExtraStorage source = open(file, DatabaseMode.READ_ONLY, context);
            TournamentExtraStorage target = new TournamentExtraStorage(context);
            source.copyEntities(target);
            return target;
        }
        return new TournamentExtraStorage(file, mode.openOptions(), context);
    }

    public @NotNull DatabaseContext context() {
        return context;
    }

    static TournamentExtraHeader peekHeader(File file) throws IOException {
        TournamentExtraStorage storage = open(file, DatabaseMode.READ_ONLY, null);
        TournamentExtraHeader header = storage.storage.getHeader();
        storage.close();
        return header;
    }

    int getStorageVersion() {
        return storage.getHeader().version();
    }

    @Override
    public int serializedHeaderSize() {
        return 32;
    }

    @Override
    public int headerSize(@NotNull TournamentExtraHeader header) {
        return 32;
    }

    public int numEntries() {
        // For some reason the .cbtt file doesn't store the number of entries, but the index
        // of the highest entry (always one less). This means the header doesn't distinguish
        // between an empty store or a store with 1 item.
        // Therefore we use isEmpty() to separate between these cases.
        return this.storage.isEmpty() ? 0 : this.storage.getHeader().highestIndex() + 1;
    }

    public @NotNull TournamentExtra get(int id) {
        // It's okay to get entries beyond the last because in this particular file,
        // ChessBase lazily adds data.
        return id < numEntries() ? this.storage.getItem(id) : TournamentExtra.empty();
    }

    public void put(int id, TournamentExtra extra) {
        // If there are missing items in the store, we need to fill up with empty items.
        for (int i = numEntries(); i < id; i++) {
            this.storage.putItem(i, TournamentExtra.empty());
        }
        this.storage.putItem(id, extra);
        if (id > this.storage.getHeader().highestIndex()) {
            this.storage.putHeader(TournamentExtraHeader.empty(id));
        }
    }

    @Override
    public long itemOffset(@NotNull TournamentExtraHeader header, int index) {
        return serializedHeaderSize() + index * (long) itemSize(header);
    }

    @Override
    public int itemSize(@NotNull TournamentExtraHeader header) {
        return header.recordSize();
    }

    @Override
    public @NotNull TournamentExtraHeader deserializeHeader(@NotNull ByteBuffer buf) {
        int version = ByteBufferUtil.getIntL(buf);
        int recordSize = ByteBufferUtil.getIntL(buf);
        int highestIndex = ByteBufferUtil.getIntL(buf);
        buf.position(buf.position() + 20);

        return ImmutableTournamentExtraHeader.builder()
                .version(version)
                .recordSize(recordSize)
                .highestIndex(highestIndex)
                .build();
    }

    @Override
    public void serializeHeader(@NotNull TournamentExtraHeader header, @NotNull ByteBuffer buf) {
        ByteBufferUtil.putIntL(buf, header.version());
        ByteBufferUtil.putIntL(buf, header.recordSize());
        ByteBufferUtil.putIntL(buf, header.highestIndex());
        ByteBufferUtil.putIntL(buf, 0);
        ByteBufferUtil.putIntL(buf, 0);
        ByteBufferUtil.putIntL(buf, 0);
        ByteBufferUtil.putIntL(buf, 0);
        ByteBufferUtil.putIntL(buf, 0);
    }

    @Override
    public @NotNull TournamentExtra deserializeItem(int id, @NotNull ByteBuffer buf, @NotNull TournamentExtraHeader header) {
        serializationStats.addDeserialization(1);

        int itemSize = storage.getHeader().recordSize();

        double latitude = ByteBufferUtil.getDoubleL(buf);
        double longitude = ByteBufferUtil.getDoubleL(buf);

        Date endDate = Date.unset();
        ArrayList<TiebreakRule> rules = new ArrayList<>();
        int numRules = 0;

        if (itemSize >= 61) {
            buf.position(buf.position() + 34);

            for (int i = 0; i < 10; i++) {
                rules.add(TiebreakRule.fromId(ByteBufferUtil.getUnsignedByte(buf)));
            }
            numRules = ByteBufferUtil.getUnsignedByte(buf);
        }
        if (itemSize >= 65) {
            endDate = CBUtil.decodeDate(ByteBufferUtil.getIntL(buf));
        }

        return ImmutableTournamentExtra.builder()
                .latitude(latitude)
                .longitude(longitude)
                .tiebreakRules(rules.subList(0, numRules))
                .endDate(endDate)
                .build();
    }

    @Override
    public void serializeItem(@NotNull TournamentExtra tournamentExtra, @NotNull ByteBuffer buf, @NotNull TournamentExtraHeader header) {
        serializationStats.addSerialization(1);

        ByteBufferUtil.putDoubleL(buf, tournamentExtra.latitude());
        ByteBufferUtil.putDoubleL(buf, tournamentExtra.longitude());

        // 34 bytes with unknown purpose, but every 11th byte is 7
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 10; i++) {
                ByteBufferUtil.putByte(buf, 0);
            }
            ByteBufferUtil.putByte(buf, 7);
        }
        ByteBufferUtil.putByte(buf, 0);

        for (TiebreakRule tiebreakRule : tournamentExtra.tiebreakRules()) {
            ByteBufferUtil.putByte(buf, tiebreakRule.id());
        }
        for (int i = 0; i < 10 - tournamentExtra.tiebreakRules().size(); i++) {
            ByteBufferUtil.putByte(buf, 0);
        }
        ByteBufferUtil.putByte(buf, tournamentExtra.tiebreakRules().size());
        ByteBufferUtil.putIntL(buf, CBUtil.encodeDate(tournamentExtra.endDate()));
    }

    public void copyEntities(@NotNull TournamentExtraStorage targetStorage) {
        // Low level copy of all entities from one storage to a new empty storage
        if (targetStorage.numEntries() != 0) {
            throw new IllegalStateException("The target storage must be empty");
        }

        int batchSize = 1000, capacity = numEntries(), currentIndex = 0;
        for (int i = 0; i < capacity; i += batchSize) {
            List<TournamentExtra> items = storage.getItems(i, Math.min(i + batchSize, capacity));
            for (TournamentExtra item : items) {
                targetStorage.storage.putItem(currentIndex, item);
                currentIndex += 1;
            }
        }
        // Copy all fields in the source header except the header size
        TournamentExtraHeader targetHeader = targetStorage.storage.getHeader();
        ImmutableTournamentExtraHeader newHeader = ImmutableTournamentExtraHeader
                .copyOf(storage.getHeader())
                .withVersion(targetHeader.version())
                .withRecordSize(targetHeader.recordSize());
        targetStorage.storage.putHeader(newHeader);
    }

    public void close() {
        this.storage.close();
    }

    /**
     * Upgrades the extra tournament storage to the latest version if necessary
     * If the file doesn't exist, it will be created.
     * @param file a .cbtt file that should be upgraded, or created if missing
     * @throws IOException if something failed during the upgrade
     */
    public static void upgrade(@NotNull File file) throws IOException {
        if (!CBUtil.extension(file).equals(".cbtt")) {
            throw new IllegalArgumentException("The extension should be .cbtt");
        }
        if (!file.exists()) {
            // No need to fill up with tournaments; it's done on demand
            TournamentExtraStorage.create(file, null);
            return;
        }

        TournamentExtraHeader currentHeader = peekHeader(file);
        if (currentHeader.version() < TournamentExtraHeader.DEFAULT_HEADER_VERSION ||
                currentHeader.recordSize() < TournamentExtraHeader.DEFAULT_RECORD_SIZE) {
            log.info(String.format("Upgrading tournament extra storage from version %d (item size %d) to version %d (item size %d)",
                    currentHeader.version(), currentHeader.recordSize(),
                    TournamentExtraHeader.DEFAULT_HEADER_VERSION, TournamentExtraHeader.DEFAULT_RECORD_SIZE));

            File upgradedStorageFile = File.createTempFile(CBUtil.baseName(file), ".cbtt");
            upgradedStorageFile.delete();

            TournamentExtraStorage oldStorage = TournamentExtraStorage.open(file, DatabaseMode.READ_ONLY, null);
            TournamentExtraStorage upgradedStorage = null;
            try {
                upgradedStorage = TournamentExtraStorage.create(upgradedStorageFile, null);
                oldStorage.copyEntities(upgradedStorage);
            } finally {
                oldStorage.close();
                if (upgradedStorage != null) {
                    upgradedStorage.close();
                }
            }

            file.delete();
            upgradedStorageFile.renameTo(file);
        }
    }

    @Override
    public @NotNull TournamentExtra emptyItem(int id) {
        return ImmutableTournamentExtra.empty();
    }
}
