package se.yarin.cbhlib.entities;

import se.yarin.cbhlib.ByteBufferUtil;

import java.nio.ByteBuffer;

class TestEntitySerializer implements EntitySerializer<TestEntity> {
    @Override
    public ByteBuffer serialize(TestEntity entity) {
        ByteBuffer buf = ByteBuffer.allocate(getSerializedEntityLength());
        ByteBufferUtil.putByteString(buf, entity.getKey(), 20);
        ByteBufferUtil.putIntB(buf, entity.getValue());
        return buf;
    }

    @Override
    public TestEntity deserialize(int entityId, ByteBuffer buffer) {
        String value = ByteBufferUtil.getFixedSizeByteString(buffer, 20);
        TestEntity testEntity = new TestEntity(entityId, value);
        testEntity.setValue(ByteBufferUtil.getIntB(buffer));
        return testEntity;
    }

    @Override
    public int getSerializedEntityLength() {
        return 24;
    }
}
