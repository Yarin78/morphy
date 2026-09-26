package se.yarin.morphy.cb2.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.jetbrains.annotations.NotNull;

/** A {@link ByteStore} backed by a file on disk, read and written directly. */
public final class FileByteStore implements ByteStore {
  private final @NotNull Path path;
  private final @NotNull FileChannel channel;
  private final boolean writable;

  /**
   * Opens a file.
   *
   * @param path the file
   * @param writable whether to open it for writing; a writable file is created if missing
   * @throws IOException if the file can't be opened
   */
  public FileByteStore(@NotNull Path path, boolean writable) throws IOException {
    this.path = path;
    this.writable = writable;
    this.channel =
        writable
            ? FileChannel.open(
                path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
            : FileChannel.open(path, StandardOpenOption.READ);
  }

  @Override
  public long size() {
    try {
      return channel.size();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to get the size of " + path, e);
    }
  }

  @Override
  public @NotNull ByteBuffer read(long offset, int length) {
    if (offset < 0 || length < 0 || offset + length > size()) {
      throw new IllegalArgumentException(
          String.format(
              "Can't read %d bytes at offset %d in %s of size %d", length, offset, path, size()));
    }
    ByteBuffer buf = ByteStore.allocate(length);
    try {
      while (buf.hasRemaining()) {
        if (channel.read(buf, offset + buf.position()) < 0) {
          throw new IOException("Unexpected end of file");
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read " + path, e);
    }
    return buf.flip();
  }

  @Override
  public void write(long offset, @NotNull ByteBuffer data) {
    requireWritable();
    if (offset < 0 || offset > size()) {
      throw new IllegalArgumentException("Can't write at offset " + offset + " in " + path);
    }
    ByteBuffer src = data.duplicate();
    try {
      long at = offset;
      while (src.hasRemaining()) {
        at += channel.write(src, at);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write " + path, e);
    }
  }

  @Override
  public void setSize(long size) {
    requireWritable();
    try {
      long current = channel.size();
      if (size < current) {
        channel.truncate(size);
      } else if (size > current) {
        channel.write(ByteBuffer.allocate(1), size - 1);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to resize " + path, e);
    }
  }

  @Override
  public boolean writable() {
    return writable;
  }

  @Override
  public void flush() {
    if (!writable) {
      return;
    }
    try {
      channel.force(false);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to flush " + path, e);
    }
  }

  @Override
  public void close() {
    try {
      channel.close();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to close " + path, e);
    }
  }

  private void requireWritable() {
    if (!writable) {
      throw new IllegalStateException(path + " is opened read-only");
    }
  }
}
