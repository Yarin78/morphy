package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.DatabaseContext;
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

public class FooEntityIndex extends EntityIndex<FooEntity> {
    public static final int SERIALIZED_FOO_SIZE = 32;

    public FooEntityIndex() {
        this(new InMemoryItemStorage<>(EntityIndexHeader.empty(SERIALIZED_FOO_SIZE)));
    }

    protected FooEntityIndex(@NotNull ItemStorage<EntityIndexHeader, EntityNode> storage) {
        this(storage, null);
    }

    protected FooEntityIndex(@NotNull ItemStorage<EntityIndexHeader, EntityNode> storage, @Nullable DatabaseContext context) {
        super(storage, EntityType.PLAYER, context);
    }

    public static @NotNull FooEntityIndex create(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return open(file, Set.of(READ, WRITE, CREATE_NEW));
    }

    public static @NotNull FooEntityIndex open(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return open(file, Set.of(READ, WRITE));
    }

    public static @NotNull FooEntityIndex open(@NotNull File file, @NotNull Set<OpenOption> options)
            throws IOException, MorphyInvalidDataException {
        return new FooEntityIndex(new FileItemStorage<>(
                file, new DatabaseContext(), "Foo", new EntityIndexSerializer(SERIALIZED_FOO_SIZE), EntityIndexHeader.empty(SERIALIZED_FOO_SIZE), options));
    }

    public static @NotNull FooEntityIndex openInMemory(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return openInMemory(file, Set.of(READ));
    }

    public static @NotNull FooEntityIndex openInMemory(@NotNull File file, @NotNull Set<OpenOption> options)
            throws IOException, MorphyInvalidDataException {
        FooEntityIndex source = open(file, options);
        FooEntityIndex target = new FooEntityIndex();
        source.copyEntities(target);
        return target;
    }

    @Override
    protected @NotNull FooEntity deserialize(int entityId, int count, int firstGameId, byte[] serializedData) {
        ByteBuffer buf = ByteBuffer.wrap(serializedData);
        return ImmutableFooEntity.builder()
                .id(entityId)
                .count(count)
                .firstGameId(firstGameId)
                .key(ByteBufferUtil.getFixedSizeByteString(buf, 20))
                .value(ByteBufferUtil.getIntB(buf))
                .build();
    }

    @Override
    protected void serialize(@NotNull FooEntity entity, @NotNull ByteBuffer buf) {
        ByteBufferUtil.putFixedSizeByteString(buf, entity.key(), 20);
        ByteBufferUtil.putIntB(buf, entity.value());
    }
}
