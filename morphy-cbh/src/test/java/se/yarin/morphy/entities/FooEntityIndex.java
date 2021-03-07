package se.yarin.morphy.entities;

import se.yarin.cbhlib.util.ByteBufferUtil;
import se.yarin.morphy.storage.ItemStorage;

import java.nio.ByteBuffer;

public class FooEntityIndex extends EntityIndex<FooEntity> {
    public static final int SERIALIZED_FOO_SIZE = 32;

    protected FooEntityIndex(ItemStorage<EntityIndexHeader, EntityNode> storage) {
        super(storage, "Foo");
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
