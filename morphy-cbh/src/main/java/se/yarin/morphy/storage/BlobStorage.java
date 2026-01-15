package se.yarin.morphy.storage;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.exceptions.MorphyIOException;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Interface for a simple blob storage. Blobs can be read and written to given their starting
 * offset. The length of the blob must be encoded in the blob.
 *
 * <p>An external index is required that keep track of where each blob starts, and to prevent that
 * blobs overwrite each other.
 */
public interface BlobStorage {
  @NotNull
  BlobStorageHeader getHeader();

  void putHeader(@NotNull BlobStorageHeader header);

  /**
   * Reads a blob from the storage.
   *
   * @param offset to offset to where the blob begins
   * @return a buffer containing the blob
   */
  ByteBuffer getBlob(long offset);

  /**
   * Gets the size of a blob from the storage.
   *
   * @param offset to offset to where the blob begins
   * @return the size of the blob stored as the given offset
   */
  int getBlobSize(long offset);

  /**
   * Writes a new blob to the end of the storage
   *
   * @param blob the blob to append
   * @return the offset in the storage to the blob
   */
  long appendBlob(@NotNull ByteBuffer blob);

  /**
   * Writes a blob to the storage at the specific offset. It's up to the caller to ensure that the
   * blob doesn't overwrite anything. If replacing an existing blob, make sure to call {@link
   * BlobStorage#removeBlob(long)}} first to correctly updated the amount of wasted bytes!
   *
   * @param offset the offset to write the blob
   * @param blob the blob to write
   */
  void putBlob(long offset, @NotNull ByteBuffer blob);

  /**
   * Marks a blob as deleted. In practice the only thing this does is update the {@link
   * BlobStorageHeader#wasted()} count
   *
   * @param offset the offset of the blob to remove
   * @return the size of the removed blob
   */
  default int removeBlob(long offset) {
    if (offset >= getSize()) {
      throw new MorphyIOException(
          String.format(
              "Tried to remove blob at offset %d but file size was %d", offset, getSize()));
    }
    int oldBlobSize = getBlobSize(offset);
    if (oldBlobSize <= 0 || oldBlobSize > getSize()) {
      // This is a simplistic attempt to validate that we're actually removing an old blob
      throw new MorphyInvalidDataException(
          String.format(
              "Tried to remove a blob at offset %d but no valid blob was there before", offset));
    }

    putHeader(
        BlobStorageHeader.of(
            this.getHeader().headerSize(), getSize(), getWastedBytes() + oldBlobSize));
    return oldBlobSize;
  }

  /**
   * Gets the current size of the storage.
   *
   * @return the size
   */
  long getSize();

  /**
   * Gets the number of bytes that are wasted in the storage; allocated but not actively used.
   *
   * @return number of bytes
   */
  default long getWastedBytes() {
    if ((int) getHeader().wastedLong() == getHeader().wasted()) {
      return getHeader().wastedLong();
    }
    return getHeader().wasted();
  }

  /**
   * Inserts the specified number of bytes at the given start position. All data after will be
   * adjusted. It's up to the caller to ensure that indices to any blob stored after the specified
   * offset is updated.
   *
   * @param offset the offset in the storage at which to insert empty bytes
   * @param noBytes the number of empty bytes to insert
   * @throws se.yarin.morphy.exceptions.MorphyIOException if an IO error occurred during the insert
   */
  void insert(long offset, long noBytes);

  /**
   * Closes the storage. Any further operations on the storage will cause IO errors.
   *
   * @throws IOException if an IO error occurs
   */
  void close() throws IOException;
}
