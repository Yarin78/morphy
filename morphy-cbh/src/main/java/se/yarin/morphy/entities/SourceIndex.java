package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.DatabaseContext;
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
        this(null);
    }

    public SourceIndex(@Nullable DatabaseContext context) {
        this(new InMemoryItemStorage<>(context, "Source", EntityIndexHeader.empty(SERIALIZED_SOURCE_SIZE)), context);
    }

    protected SourceIndex(@NotNull File file, @NotNull Set<OpenOption> openOptions, @NotNull DatabaseContext context) throws IOException {
        this(new FileItemStorage<>(
                file, context, "Source", new EntityIndexSerializer(SERIALIZED_SOURCE_SIZE), EntityIndexHeader.empty(SERIALIZED_SOURCE_SIZE), openOptions), context);
    }

    protected SourceIndex(@NotNull ItemStorage<EntityIndexHeader, EntityNode> storage, @Nullable DatabaseContext context) {
        super(storage, "Source", context);
    }

    public static @NotNull SourceIndex create(@NotNull File file, @Nullable DatabaseContext context)
            throws IOException, MorphyInvalidDataException {
        return new SourceIndex(file, Set.of(READ, WRITE, CREATE_NEW), context == null ? new DatabaseContext() : context);
    }

    public static @NotNull SourceIndex open(@NotNull File file, @Nullable DatabaseContext context)
            throws IOException, MorphyInvalidDataException {
        return open(file, DatabaseMode.READ_WRITE, context);
    }

    public static @NotNull SourceIndex open(@NotNull File file, @NotNull DatabaseMode mode, @Nullable DatabaseContext context)
            throws IOException, MorphyInvalidDataException {
        if (mode == DatabaseMode.IN_MEMORY) {
            SourceIndex source = open(file, DatabaseMode.READ_ONLY, context);
            SourceIndex target = new SourceIndex(context);
            source.copyEntities(target);
            return target;
        }
        return new SourceIndex(file, mode.openOptions(), context == null ? new DatabaseContext() : context);
    }

    @Override
    protected @NotNull Source deserialize(int entityId, int count, int firstGameId, byte[] serializedData) {
        itemMetricsRef().update(metrics -> metrics.addDeserialization(1));
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
        itemMetricsRef().update(metrics -> metrics.addSerialization(1));
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
