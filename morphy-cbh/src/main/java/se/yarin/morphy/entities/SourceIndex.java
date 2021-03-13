package se.yarin.morphy.entities;

import se.yarin.cbhlib.entities.SourceQuality;
import se.yarin.cbhlib.util.ByteBufferUtil;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.storage.FileItemStorage;
import se.yarin.morphy.storage.InMemoryItemStorage;
import se.yarin.morphy.storage.ItemStorage;
import se.yarin.morphy.storage.OpenOption;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;

public class SourceIndex extends EntityIndex<Source> {
    private static final int SERIALIZED_SOURCE_SIZE = 59;

    public SourceIndex() {
        this(new InMemoryItemStorage<>(EntityIndexHeader.empty(SERIALIZED_SOURCE_SIZE), OpenOption.RW()));
    }

    protected SourceIndex(ItemStorage<EntityIndexHeader, EntityNode> storage) {
        super(storage, "Source");
    }

    public static SourceIndex open(File file, OpenOption... options)
            throws IOException, MorphyInvalidDataException {
        OpenOption.validate(options);
        Set<OpenOption> optionSet = Set.of(options);
        return new SourceIndex(new FileItemStorage<>(file, new EntityIndexSerializer(SERIALIZED_SOURCE_SIZE), optionSet));
    }

    @Override
    protected Source deserialize(int entityId, int count, int firstGameId, byte[] serializedData) {
        ByteBuffer buf = ByteBuffer.wrap(serializedData);
        return ImmutableSource.builder()
                .id(entityId)
                .count(count)
                .firstGameId(firstGameId)
                .title(ByteBufferUtil.getFixedSizeByteString(buf, 25))
                .publisher(ByteBufferUtil.getFixedSizeByteString(buf, 16))
                .publication(CBUtil.decodeDate(ByteBufferUtil.getIntL(buf)))
                .date(CBUtil.decodeDate(ByteBufferUtil.getIntL(buf)))
                .version(ByteBufferUtil.getUnsignedByte(buf))
                .quality(SourceQuality.values()[ByteBufferUtil.getUnsignedByte(buf)])
                .build();
    }

    @Override
    protected void serialize(Source source, ByteBuffer buf) {
        ByteBufferUtil.putFixedSizeByteString(buf, source.title(), 25);
        ByteBufferUtil.putFixedSizeByteString(buf, source.publisher(), 16);
        ByteBufferUtil.putIntL(buf, CBUtil.encodeDate(source.publication()));
        ByteBufferUtil.putIntL(buf, CBUtil.encodeDate(source.date()));
        ByteBufferUtil.putByte(buf, source.version());
        ByteBufferUtil.putByte(buf, source.quality().ordinal());
    }
}
