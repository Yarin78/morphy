package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
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

public class FooBarEntityIndex extends EntityIndex<FooBarEntity> {
    public static final int SERIALIZED_FOOBAR_SIZE = 56;

    public FooBarEntityIndex() {
        this(new InMemoryItemStorage<>(EntityIndexHeader.empty(SERIALIZED_FOOBAR_SIZE)));
    }

    protected FooBarEntityIndex(ItemStorage<EntityIndexHeader, EntityNode> storage) {
        super(storage, "FooBar");
    }

    public static @NotNull FooBarEntityIndex create(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return open(file, Set.of(READ, WRITE, CREATE_NEW));
    }

    public static @NotNull FooBarEntityIndex open(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return open(file, Set.of(READ, WRITE));
    }

    public static @NotNull FooBarEntityIndex open(@NotNull File file, @NotNull Set<OpenOption> options)
            throws IOException, MorphyInvalidDataException {
        return new FooBarEntityIndex(new FileItemStorage<>(
                file, new EntityIndexSerializer(SERIALIZED_FOOBAR_SIZE), EntityIndexHeader.empty(SERIALIZED_FOOBAR_SIZE), options));
    }

    public static @NotNull FooBarEntityIndex openInMemory(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return openInMemory(file, Set.of(READ));
    }

    public static @NotNull FooBarEntityIndex openInMemory(@NotNull File file, @NotNull Set<OpenOption> options)
            throws IOException, MorphyInvalidDataException {
        FooBarEntityIndex source = open(file, options);
        FooBarEntityIndex target = new FooBarEntityIndex();
        source.copyEntities(target);
        return target;
    }

    @Override
    protected @NotNull FooBarEntity deserialize(int entityId, int count, int firstGameId, byte[] serializedData) {
        ByteBuffer buf = ByteBuffer.wrap(serializedData);
        return ImmutableFooBarEntity.builder()
                .id(entityId)
                .key(ByteBufferUtil.getFixedSizeByteString(buf, 20))
                .value(ByteBufferUtil.getIntB(buf))
                .extraValue(ByteBufferUtil.getIntB(buf))
                .extraString(ByteBufferUtil.getFixedSizeByteString(buf, 20))
                .build();
    }

    @Override
    protected void serialize(@NotNull FooBarEntity entity, @NotNull ByteBuffer buf) {
        ByteBufferUtil.putFixedSizeByteString(buf, entity.key(), 20);
        ByteBufferUtil.putIntB(buf, entity.value());
        ByteBufferUtil.putIntB(buf, entity.extraValue());
        ByteBufferUtil.putFixedSizeByteString(buf, entity.extraString(), 20);
    }
}
