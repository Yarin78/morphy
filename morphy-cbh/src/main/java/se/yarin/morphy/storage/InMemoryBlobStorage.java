package se.yarin.morphy.storage;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import static se.yarin.morphy.storage.BlobStorageHeader.DEFAULT_SERIALIZED_HEADER_SIZE;

public class InMemoryBlobStorage implements BlobStorage {
  // This version of the InMemoryBlobStorage serializes its data, which makes it more similar to
  // FileBlobStorage.
  // But this is obviously a bit slower and makes it a bit different from InMemoryItemStorage.
  // Consider making a non-serialized version, but ensure that the stored blobs are immutable! (or
  // deep cloned)

  private BlobStorageHeader header;
  private final BlobSizeRetriever blobSizeRetriever;
  private ByteBuffer data;

  public InMemoryBlobStorage(@NotNull BlobSizeRetriever blobSizeRetriever) {
    this(ByteBuffer.allocate(DEFAULT_SERIALIZED_HEADER_SIZE).limit(0), blobSizeRetriever);
  }

  public InMemoryBlobStorage(
      @NotNull ByteBuffer data, @NotNull BlobSizeRetriever blobSizeRetriever) {
    this.data = data;
    this.blobSizeRetriever = blobSizeRetriever;
    if (data.limit() == 0) {
      putHeader(BlobStorageHeader.empty());
    } else {
      refreshHeader();
    }
  }

  private void refreshHeader() throws MorphyInvalidDataException {
    ByteBuffer buf = data.slice(0, DEFAULT_SERIALIZED_HEADER_SIZE);
    BlobStorageHeader header = BlobStorageHeader.deserialize(buf);
    header.validate(true, "InMemory", this.data.limit());
    this.header = header;
  }

  private void grow() {
    int oldPos = data.position();
    int oldLimit = data.limit();
    ByteBuffer newBuffer = ByteBuffer.allocate(data.capacity() * 2);
    data.position(0);
    newBuffer.put(data);
    newBuffer.position(oldPos);
    newBuffer.limit(oldLimit);
    data = newBuffer;
  }

  @Override
  public @NotNull BlobStorageHeader getHeader() {
    return this.header;
  }

  @Override
  public void putHeader(@NotNull BlobStorageHeader header) {
    ByteBuffer buf = header.serialize();
    data.position(0);
    if (data.limit() < buf.limit()) {
      data.limit(buf.limit());
    }
    data.put(buf);
    this.header = header;
  }

  @Override
  public @NotNull ByteBuffer getBlob(long offset) {
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be non-negative");
    }

    try {
      int blobSize =
          blobSizeRetriever.getBlobSize(data.slice((int) offset, data.limit() - (int) offset));
      ByteBuffer buf = ByteBuffer.allocate(blobSize);
      buf.put(data.slice((int) offset, blobSize));
      buf.position(0);
      return buf;
    } catch (BufferUnderflowException e) {
      throw new MorphyInvalidDataException("Failed to get blob at offset " + offset);
    }
  }

  @Override
  public @NotNull int getBlobSize(long offset) {
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be non-negative");
    }

    try {
      return blobSizeRetriever.getBlobSize(data.slice((int) offset, data.limit() - (int) offset));
    } catch (BufferUnderflowException e) {
      throw new MorphyInvalidDataException("Failed to get blob at offset " + offset);
    }
  }

  @Override
  public long appendBlob(@NotNull ByteBuffer blob) {
    long offset = data.limit();
    putBlob(offset, blob);
    return offset;
  }

  @Override
  public void putBlob(long offset, @NotNull ByteBuffer blob) {
    if (offset > data.limit()) {
      throw new MorphyInvalidDataException(
          String.format(
              "Tried to put blob at offset %d but buffer size was %d", offset, data.limit()));
    }
    while (offset + blob.limit() > data.capacity()) {
      grow();
    }
    long wasted = getWastedBytes();
    if (offset < data.limit()) {
      wasted -= Math.min(data.limit() - offset, blob.limit());
    }
    data.position((int) offset);
    data.limit((int) Math.max(data.limit(), offset + blob.limit()));
    data.put(blob);

    putHeader(BlobStorageHeader.of(this.header.headerSize(), getSize(), wasted));
  }

  @Override
  public long getSize() {
    return data.limit();
  }

  @Override
  public void insert(long offset, long noBytes) {
    while (data.limit() + noBytes > data.capacity()) {
      grow();
    }
    data.limit((int) (data.limit() + noBytes));
    for (int i = data.limit() - 1; i >= offset + noBytes; i--) {
      byte b = data.get((int) (i - noBytes));
      data.put(i, b);
    }
    putHeader(
        BlobStorageHeader.of(this.header.headerSize(), getSize(), getWastedBytes() + noBytes));
  }

  @Override
  public void close() throws IOException {}
}
