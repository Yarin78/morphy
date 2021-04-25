package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.DatabaseMode;
import se.yarin.morphy.util.CBUtil;
import se.yarin.util.ByteBufferUtil;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.morphy.storage.FileItemStorage;
import se.yarin.morphy.storage.InMemoryItemStorage;
import se.yarin.morphy.storage.ItemStorage;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.OpenOption;
import java.util.Set;

import static java.nio.file.StandardOpenOption.*;

public class SourceIndex extends EntityIndex<Source> {
    private static final int SERIALIZED_SOURCE_SIZE = 59;

    public SourceIndex() {
        this(new InMemoryItemStorage<>(EntityIndexHeader.empty(SERIALIZED_SOURCE_SIZE)));
    }

    protected SourceIndex(@NotNull File file, @NotNull Set<OpenOption> openOptions) throws IOException {
        this(new FileItemStorage<>(
                file, new EntityIndexSerializer(SERIALIZED_SOURCE_SIZE), EntityIndexHeader.empty(SERIALIZED_SOURCE_SIZE), openOptions));
    }

    protected SourceIndex(ItemStorage<EntityIndexHeader, EntityNode> storage) {
        super(storage, "Source");
    }

    public static @NotNull SourceIndex create(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return new SourceIndex(file, Set.of(READ, WRITE, CREATE_NEW));
    }

    public static @NotNull SourceIndex open(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return open(file, DatabaseMode.READ_WRITE);
    }

    public static @NotNull SourceIndex open(@NotNull File file, @NotNull DatabaseMode mode)
            throws IOException, MorphyInvalidDataException {
        if (mode == DatabaseMode.IN_MEMORY) {
            SourceIndex source = open(file, DatabaseMode.READ_ONLY);
            SourceIndex target = new SourceIndex();
            source.copyEntities(target);
            return target;
        }
        return new SourceIndex(file, mode.openOptions());
    }

    @Override
    protected @NotNull Source deserialize(int entityId, int count, int firstGameId, byte[] serializedData) {
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
    protected void serialize(@NotNull Source source, @NotNull ByteBuffer buf) {
        ByteBufferUtil.putFixedSizeByteString(buf, source.title(), 25);
        ByteBufferUtil.putFixedSizeByteString(buf, source.publisher(), 16);
        ByteBufferUtil.putIntL(buf, CBUtil.encodeDate(source.publication()));
        ByteBufferUtil.putIntL(buf, CBUtil.encodeDate(source.date()));
        ByteBufferUtil.putByte(buf, source.version());
        ByteBufferUtil.putByte(buf, source.quality().ordinal());
    }

    public static void upgrade(@NotNull File file) throws IOException {
        EntityIndex.upgrade(file, new EntityIndexSerializer(SERIALIZED_SOURCE_SIZE));
    }
}
