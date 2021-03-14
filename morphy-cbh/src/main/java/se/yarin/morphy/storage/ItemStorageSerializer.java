package se.yarin.morphy.storage;

import se.yarin.morphy.exceptions.MorphyInvalidDataException;

import java.nio.ByteBuffer;

public interface ItemStorageSerializer<THeader, TItem> {
    int expectedHeaderSize();
    long itemOffset(THeader header, int index);

    /**
     * The size of an item record when reading from the storage, according to the file metadata.
     */
    int itemSize(THeader header);

    THeader deserializeHeader(ByteBuffer buf) throws MorphyInvalidDataException;

    /**
     * Deserialized an item and updates the buffer pointer to point to the next item in the buffer
     * (or the end of the buffer)
     */
    TItem deserializeItem(int id, ByteBuffer buf);

    void serializeHeader(THeader header, ByteBuffer buf);
    void serializeItem(TItem item, ByteBuffer buf);
}
