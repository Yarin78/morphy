package se.yarin.cbhlib.entities;

import se.yarin.cbhlib.ByteBufferUtil;

import java.nio.ByteBuffer;

class TestEntityv2Serializer implements EntitySerializer<TestEntityv2> {
    @Override
    public ByteBuffer serialize(TestEntityv2 entity) {
        ByteBuffer buf = ByteBuffer.allocate(getSerializedEntityLength());
        ByteBufferUtil.putByteString(buf, entity.getKey(), 20);
        ByteBufferUtil.putIntB(buf, entity.getValue());
        ByteBufferUtil.putIntB(buf, entity.getExtraValue());
        ByteBufferUtil.putByteString(buf, entity.getExtraString(), 20);
        return buf;
    }

    @Override
    public TestEntityv2 deserialize(int entityId, ByteBuffer buffer) {
        String value = ByteBufferUtil.getFixedSizeByteString(buffer, 20);
        TestEntityv2 testEntity = new TestEntityv2(entityId, value);
        testEntity.setValue(ByteBufferUtil.getIntB(buffer));
        testEntity.setExtraValue(ByteBufferUtil.getIntB(buffer));
        testEntity.setExtraString(ByteBufferUtil.getFixedSizeByteString(buffer, 20));
        return testEntity;
    }

    @Override
    public int getSerializedEntityLength() {
        return 48;
    }
}
