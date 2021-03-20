package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import se.yarin.cbhlib.util.ByteBufferUtil;
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

    protected FooEntityIndex(ItemStorage<EntityIndexHeader, EntityNode> storage) {
        super(storage, "Foo");
    }

    public static FooEntityIndex create(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return open(file, Set.of(READ, WRITE, CREATE_NEW), true);
    }

    public static FooEntityIndex open(@NotNull File file)
            throws IOException, MorphyInvalidDataException {
        return open(file, Set.of(READ, WRITE), true);
    }

    public static FooEntityIndex open(@NotNull File file, @NotNull Set<OpenOption> options, boolean strict)
            throws IOException, MorphyInvalidDataException {
        return new FooEntityIndex(new FileItemStorage<>(
                file, new EntityIndexSerializer(SERIALIZED_FOO_SIZE), EntityIndexHeader.empty(SERIALIZED_FOO_SIZE), options, strict));
    }

    public static FooEntityIndex openInMemory(@NotNull File file, @NotNull Set<OpenOption> options, boolean strict)
            throws IOException, MorphyInvalidDataException {
        FooEntityIndex source = open(file, options, strict);
        FooEntityIndex target = new FooEntityIndex();
        source.copyEntities(target);
        return target;
    }

    @Override
    protected FooEntity deserialize(int entityId, int count, int firstGameId, byte[] serializedData) {
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
    protected void serialize(FooEntity entity, ByteBuffer buf) {
        ByteBufferUtil.putFixedSizeByteString(buf, entity.key(), 20);
        ByteBufferUtil.putIntB(buf, entity.value());
    }
}
