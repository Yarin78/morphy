package se.yarin.morphy.storage;

import java.nio.ByteBuffer;

public interface BlobSizeRetriever {
  /**
   * Gets the size of a blob stored in a {@link BlobStorage}. The position in buf will not be
   * modified by this operation.
   *
   * @param buf a buffer where the current position is the start of the blob
   * @return the size of the blob
   */
  int getBlobSize(ByteBuffer buf);
}
