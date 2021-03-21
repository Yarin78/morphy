package se.yarin.cbhlib.storage;

import se.yarin.util.ByteBufferUtil;

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
