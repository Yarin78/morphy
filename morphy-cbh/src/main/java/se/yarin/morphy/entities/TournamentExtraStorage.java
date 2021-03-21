package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import se.yarin.util.ByteBufferUtil;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.chess.Date;
import se.yarin.morphy.exceptions.MorphyNotSupportedException;
import se.yarin.morphy.storage.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Represents the .cbtt file containing additional Tournament information.
 * May contain fewer entries than the .cbt file as entries are only written if they contain any real data,
 * or if needed to fill up the space up until such an entry.
 */
public class TournamentExtraStorage implements ItemStorageSerializer<TournamentExtraHeader, TournamentExtra> {

    private final @NotNull ItemStorage<TournamentExtraHeader, TournamentExtra> storage;

    public TournamentExtraStorage() {
        this.storage = new InMemoryItemStorage<>(TournamentExtraHeader.empty(), TournamentExtra.empty());
    }

    public TournamentExtraStorage(@NotNull ItemStorage<TournamentExtraHeader, TournamentExtra> storage) {
        this.storage = storage;
    }

    private TournamentExtraStorage(@NotNull File file, Set<OpenOption> options) throws IOException {
        this.storage = new FileItemStorage<>(file, this, TournamentExtraHeader.empty(), options);

        if (options.contains(StandardOpenOption.WRITE)) {
            if (storage.getHeader().version() < TournamentExtraHeader.DEFAULT_HEADER_VERSION) {
                throw new MorphyNotSupportedException("Old extra tournament storage version; upgrade needed but not yet supported.");
            }
            if (storage.getHeader().version() > TournamentExtraHeader.DEFAULT_HEADER_VERSION) {
                throw new MorphyNotSupportedException("Newer unsupported extra tournament storage format; writing not possible.");
            }
            if (storage.getHeader().recordSize() != TournamentExtraHeader.DEFAULT_RECORD_SIZE) {
                // This shouldn't happen because of the version checks above, so this is mostly a sanity check
                // to ensure that same version doesn't have different record sizes.
                throw new MorphyNotSupportedException(String.format("Size of extra tournament record was %d but expected %d; writing not possible.",
                        storage.getHeader().recordSize(), TournamentExtraHeader.DEFAULT_RECORD_SIZE));
            }
        }
    }

    public static TournamentExtraStorage open(@NotNull File file, Set<OpenOption> options) throws IOException {
        return new TournamentExtraStorage(file, options);
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

    public TournamentExtra get(int id) {
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
    public long itemOffset(TournamentExtraHeader header, int index) {
        return serializedHeaderSize() + index * (long) itemSize(header);
    }

    @Override
    public int itemSize(TournamentExtraHeader header) {
        return header.recordSize();
    }

    @Override
    public TournamentExtraHeader deserializeHeader(ByteBuffer buf) {
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
    public void serializeHeader(TournamentExtraHeader header, ByteBuffer buf) {
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
    public TournamentExtra deserializeItem(int id, ByteBuffer buf) {
        double latitude = ByteBufferUtil.getDoubleL(buf);
        double longitude = ByteBufferUtil.getDoubleL(buf);
        buf.position(buf.position() + 34);

        ArrayList<TiebreakRule> rules = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rules.add(TiebreakRule.fromId(ByteBufferUtil.getUnsignedByte(buf)));
        }
        int numRules = ByteBufferUtil.getUnsignedByte(buf);
        Date endDate = CBUtil.decodeDate(ByteBufferUtil.getIntL(buf));

        return ImmutableTournamentExtra.builder()
                .latitude(latitude)
                .longitude(longitude)
                .tiebreakRules(rules.subList(0, numRules))
                .endDate(endDate)
                .build();
    }

    @Override
    public void serializeItem(TournamentExtra tournamentExtra, ByteBuffer buf) {
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

    public void copyEntities(TournamentExtraStorage targetStorage) {
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
}
