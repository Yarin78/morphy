package se.yarin.morphy.storage;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;

import java.nio.ByteBuffer;

public interface ItemStorageSerializer<THeader, TItem> {
    /**
     * The number of bytes the header will occupy when serializing it.
     * @return the number of bytes a written header will occupy in the storage.
     */
    int serializedHeaderSize();
    long itemOffset(@NotNull THeader header, int index);

    /**
     * The size of an item record when reading from the storage, according to the file metadata.
     */
    int itemSize(@NotNull THeader header);

    /**
     * The actual size of the header, according to the file metadata.
     * This may differ from the {@link #serializedHeaderSize()}, in which case writing is not possible.
     * @param header the header containing the metadata (about itself)
     * @return the number of bytes this header occupied in the storage.
     */
    int headerSize(@NotNull THeader header);

    @NotNull THeader deserializeHeader(@NotNull ByteBuffer buf) throws MorphyInvalidDataException;

    /**
     * Deserialized an item and updates the buffer pointer to point to the next item in the buffer
     * (or the end of the buffer)
     */
    @NotNull TItem deserializeItem(int id, @NotNull ByteBuffer buf);

    void serializeHeader(@NotNull THeader header, @NotNull ByteBuffer buf);
    void serializeItem(@NotNull TItem item, @NotNull ByteBuffer buf);
}
