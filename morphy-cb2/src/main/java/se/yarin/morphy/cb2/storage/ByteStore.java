package se.yarin.morphy.cb2.storage;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.api.AccessMode;

/**
 * The bytes of one database file, read and written at absolute offsets. Every file of a v2
 * database sits on one of these; what the bytes mean is up to the class using it.
 *
 * <p>Buffers returned by {@link #read} are little-endian, the byte order of most of the format;
 * callers reading a big-endian structure change the order themselves.
 */
public interface ByteStore extends AutoCloseable {

  /** The current size in bytes. */
  long size();

  /**
   * Reads bytes.
   *
   * @param offset where to start
   * @param length how many bytes to read
   * @return a little-endian buffer positioned at 0 holding exactly {@code length} bytes
   * @throws IllegalArgumentException if the range reaches beyond the end of the store
   */
  @NotNull
  ByteBuffer read(long offset, int length);

  /**
   * Writes the remaining bytes of a buffer, growing the store if they reach beyond its end.
   *
   * @param offset where to start; at most {@link #size()}
   * @param data the bytes to write, from its position to its limit; its position is unchanged
   */
  void write(long offset, @NotNull ByteBuffer data);

  /** Shrinks or grows (with zeros) the store to exactly this size. */
  void setSize(long size);

  /** Whether the store may be written. */
  boolean writable();

  /** Writes any buffered changes to disk. A no-op for a store in memory. */
  void flush();

  @Override
  void close();

  /** Reads the whole store. */
  default @NotNull ByteBuffer readAll() {
    return read(0, Math.toIntExact(size()));
  }

  /** Allocates a little-endian buffer. */
  static @NotNull ByteBuffer allocate(int size) {
    return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Opens a file.
   *
   * @param file the file
   * @param mode {@link AccessMode#IN_MEMORY} loads the file into memory, where changes stay
   * @return the store
   * @throws UncheckedIOException if the file can't be opened
   */
  static @NotNull ByteStore open(@NotNull File file, @NotNull AccessMode mode) {
    try {
      return switch (mode) {
        case READ_ONLY -> new FileByteStore(file.toPath(), false);
        case READ_WRITE -> new FileByteStore(file.toPath(), true);
        case IN_MEMORY -> new MemoryByteStore(Files.readAllBytes(file.toPath()));
      };
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to open " + file, e);
    }
  }
}
