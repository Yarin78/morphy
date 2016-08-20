package se.yarin.cbhlib.entities;

import se.yarin.cbhlib.ByteBufferUtil;

import java.nio.ByteBuffer;

class TestEntityv2Serializer implements EntitySerializer<TestEntityv2> {
    @Override
    public ByteBuffer serialize(TestEntityv2 entity) {
        ByteBuffer buf = ByteBuffer.allocate(getSerializedEntityLength());
        ByteBufferUtil.putFixedSizeByteString(buf, entity.getKey(), 20);
        ByteBufferUtil.putIntB(buf, entity.getValue());
        ByteBufferUtil.putIntB(buf, entity.getExtraValue());
        ByteBufferUtil.putFixedSizeByteString(buf, entity.getExtraString(), 20);
        return buf;
    }

    @Override
    public TestEntityv2 deserialize(int entityId, ByteBuffer buffer) {
        return TestEntityv2.builder()
                .id(entityId)
                .key(ByteBufferUtil.getFixedSizeByteString(buffer, 20))
                .value(ByteBufferUtil.getIntB(buffer))
                .extraValue(ByteBufferUtil.getIntB(buffer))
                .extraString(ByteBufferUtil.getFixedSizeByteString(buffer, 20))
                .build();
    }

    @Override
    public int getSerializedEntityLength() {
        return 48;
    }
}
