package se.yarin.morphy.boosters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Instrumentation;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.storage.ItemStorageSerializer;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class IndexSerializer implements ItemStorageSerializer<IndexHeader, IndexItem> {
    private @NotNull Instrumentation.SerializationStats serializationStats;

    public IndexSerializer(Instrumentation.SerializationStats serializationStats) {
        this.serializationStats = serializationStats;
    }

    @Override
    public int serializedHeaderSize() {
        return 12;
    }

    @Override
    public long itemOffset(@NotNull IndexHeader indexHeader, int index) {
        return 12 + (long) indexHeader.itemSize() * index;
    }

    @Override
    public int itemSize(@NotNull IndexHeader indexHeader) {
        return indexHeader.itemSize();
    }

    @Override
    public int headerSize(@NotNull IndexHeader indexHeader) {
        return 12;
    }

    @Override
    public @NotNull IndexHeader deserializeHeader(@NotNull ByteBuffer buf) throws MorphyInvalidDataException {
        return ImmutableIndexHeader.builder()
                .itemSize(ByteBufferUtil.getIntL(buf))
                .unknown1(ByteBufferUtil.getIntL(buf))
                .unknown2(ByteBufferUtil.getIntL(buf))
                .build();
    }

    @Override
    public @NotNull IndexItem deserializeItem(int id, @NotNull ByteBuffer buf, @NotNull IndexHeader header) {
        serializationStats.addDeserialization(1);

        int numInts = header.itemSize() / 4;
        int[] ints = new int[numInts];
        for (int i = 0; i < numInts; i++) {
            ints[i] = ByteBufferUtil.getIntL(buf);
        }
        return ImmutableIndexItem.builder()
                .headTails(ints)
                .build();
    }

    @Override
    public @NotNull IndexItem emptyItem(int id) {
        return IndexItem.emptyCIT();
    }

    @Override
    public void serializeHeader(@NotNull IndexHeader indexHeader, @NotNull ByteBuffer buf) {
        ByteBufferUtil.putIntL(buf, indexHeader.itemSize());
        ByteBufferUtil.putIntL(buf, indexHeader.unknown1());
        ByteBufferUtil.putIntL(buf, indexHeader.unknown2());
    }

    @Override
    public void serializeItem(@NotNull IndexItem indexItem, @NotNull ByteBuffer buf, @NotNull IndexHeader header) {
        serializationStats.addSerialization(1);

        int numInts = header.itemSize() / 4;
        assert indexItem.headTails().length == numInts;
        for (int i = 0; i < numInts; i++) {
            ByteBufferUtil.putIntL(buf, indexItem.headTails()[i]);
        }
    }
}
