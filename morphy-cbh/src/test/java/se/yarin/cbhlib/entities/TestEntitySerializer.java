package se.yarin.cbhlib.entities;

import se.yarin.cbhlib.ByteBufferUtil;
import se.yarin.cbhlib.entities.storage.EntitySerializer;

import java.nio.ByteBuffer;

class TestEntitySerializer implements EntitySerializer<TestEntity> {
    @Override
    public ByteBuffer serialize(TestEntity entity) {
        ByteBuffer buf = ByteBuffer.allocate(getSerializedEntityLength());
        ByteBufferUtil.putFixedSizeByteString(buf, entity.getKey(), 20);
        ByteBufferUtil.putIntB(buf, entity.getValue());
        return buf;
    }

    @Override
    public TestEntity deserialize(int entityId, ByteBuffer buffer) {
        return TestEntity.builder()
                .id(entityId)
                .key(ByteBufferUtil.getFixedSizeByteString(buffer, 20))
                .value(ByteBufferUtil.getIntB(buffer))
                .build();
    }

    @Override
    public int getSerializedEntityLength() {
        return 24;
    }
}
