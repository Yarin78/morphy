package se.yarin.cbhlib;

import java.nio.ByteBuffer;

public interface EntitySerializer<T> {
    ByteBuffer serialize(T entity);
    T deserialize(int entityId, ByteBuffer buffer);
    int getSerializedEntityLength();
}
