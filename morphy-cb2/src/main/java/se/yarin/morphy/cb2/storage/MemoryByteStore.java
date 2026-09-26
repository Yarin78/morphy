package se.yarin.morphy.cb2.storage;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

/** A {@link ByteStore} held in memory. Nothing written to it reaches a disk. */
public final class MemoryByteStore implements ByteStore {
  private byte[] data;
  private int size;

  /** An empty store. */
  public MemoryByteStore() {
    this(new byte[0]);
  }

  /** A store holding these bytes, which it takes over. */
  public MemoryByteStore(byte @NotNull [] data) {
    this.data = data;
    this.size = data.length;
  }

  @Override
  public long size() {
    return size;
  }

  @Override
  public @NotNull ByteBuffer read(long offset, int length) {
    if (offset < 0 || length < 0 || offset + length > size) {
      throw new IllegalArgumentException(
          String.format("Can't read %d bytes at offset %d of %d", length, offset, size));
    }
    ByteBuffer buf = ByteStore.allocate(length);
    buf.put(data, (int) offset, length);
    return buf.flip();
  }

  @Override
  public void write(long offset, @NotNull ByteBuffer src) {
    if (offset < 0 || offset > size) {
      throw new IllegalArgumentException("Can't write at offset " + offset + " of " + size);
    }
    int length = src.remaining();
    ensureCapacity(Math.toIntExact(offset + length));
    src.duplicate().get(data, (int) offset, length);
    size = Math.max(size, (int) offset + length);
  }

  @Override
  public void setSize(long newSize) {
    int n = Math.toIntExact(newSize);
    ensureCapacity(n);
    if (n < size) {
      Arrays.fill(data, n, size, (byte) 0);
    }
    size = n;
  }

  private void ensureCapacity(int capacity) {
    if (capacity > data.length) {
      data = Arrays.copyOf(data, Math.max(capacity, data.length * 3 / 2 + 16));
    }
  }

  @Override
  public boolean writable() {
    return true;
  }

  @Override
  public void flush() {}

  @Override
  public void close() {}
}
