package se.yarin.morphy.entities;

import se.yarin.cbhlib.util.ByteBufferUtil;
import se.yarin.morphy.storage.ItemStorage;

import java.nio.ByteBuffer;

public class FooBarEntityIndex extends EntityIndex<FooBarEntity> {
    protected FooBarEntityIndex(ItemStorage<EntityIndexHeader, EntityNode> storage) {
        super(storage, "FooBar");
    }

    @Override
    protected FooBarEntity deserialize(int entityId, int count, int firstGameId, byte[] serializedData) {
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
    protected void serialize(FooBarEntity entity, ByteBuffer buf) {
        ByteBufferUtil.putFixedSizeByteString(buf, entity.key(), 20);
        ByteBufferUtil.putIntB(buf, entity.value());
        ByteBufferUtil.putIntB(buf, entity.extraValue());
        ByteBufferUtil.putFixedSizeByteString(buf, entity.extraString(), 20);
    }
}
