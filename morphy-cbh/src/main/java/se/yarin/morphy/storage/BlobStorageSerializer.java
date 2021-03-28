package se.yarin.morphy.storage;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;

import java.nio.ByteBuffer;

public interface BlobStorageSerializer<THeader, TBlob> {
    /**
     * The number of bytes the header will occupy when serializing it.
     * @return the number of bytes a written header will occupy in the storage.
     */
    int serializedHeaderSize();

    /**
     * The actual size of the header, according to the file metadata.
     * This may differ from the {@link #serializedHeaderSize()}, in which case writing is not possible.
     * @param header the header containing the metadata (about itself)
     * @return the number of bytes this header occupied in the storage.
     */
    int headerSize(@NotNull THeader header);

    @NotNull THeader deserializeHeader(@NotNull ByteBuffer buf) throws MorphyInvalidDataException;

    /**
     * Deserializes a blob
     */
    @NotNull TBlob deserializeBlob(@NotNull ByteBuffer buf);

    void serializeHeader(@NotNull THeader header, @NotNull ByteBuffer buf);
    void serializeBlob(@NotNull TBlob blob, @NotNull ByteBuffer buf);

    /**
     * Gets the size of a blob
     * The position in buf will not be modified by this operation.
     * @param buf a buffer where the current position is the start of the blob
     * @return the size of the blob
     */
    int getBlobSize(ByteBuffer buf);

    /**
     * The number of bytes that should be read from storage before knowing how large the blob is.
     * @return number of bytes
     */
    int prefetchSize();
}
