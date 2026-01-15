package se.yarin.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;

public class BlobChannelImpl implements BlobChannel {
  private static final int DEFAULT_INSERT_CHUNK_SIZE = 1024 * 1024;
  private final FileChannel channel;
  private int chunkSize;
  private long size;

  public BlobChannelImpl(FileChannel channel) throws IOException {
    this.channel = channel;
    this.chunkSize = DEFAULT_INSERT_CHUNK_SIZE;
    this.size = channel.size();
  }

  public static BlobChannelImpl open(Path path, OpenOption... openOptions) throws IOException {
    return new BlobChannelImpl(FileChannel.open(path, openOptions));
  }

  public void setChunkSize(int chunkSize) {
    this.chunkSize = chunkSize;
  }

  @Override
  public ByteBuffer read(long offset, int length) throws IOException {
    channel.position(offset);
    ByteBuffer buf = ByteBuffer.allocate(length);
    channel.read(buf);
    buf.flip();
    return buf;
  }

  @Override
  public void read(long offset, ByteBuffer buf) throws IOException {
    channel.position(offset);
    channel.read(buf);
  }

  @Override
  public int append(ByteBuffer buf) throws IOException {
    return write(size, buf);
  }

  @Override
  public int write(long offset, ByteBuffer buf) throws IOException {
    channel.position(offset);
    int written = channel.write(buf);
    size = Math.max(size, offset + written);
    return written;
  }

  @Override
  public void insert(long offset, long noBytes) throws IOException {
    if (noBytes < 0) {
      throw new IllegalArgumentException("Number of bytes to insert must be non-negative");
    }
    if (noBytes == 0) {
      return;
    }
    ByteBuffer buf = ByteBuffer.allocateDirect(chunkSize);
    long pos = channel.size();
    while (pos > offset) {
      // Invariant: All bytes at position pos and after have been shifted noBytes bytes
      pos -= chunkSize;
      int length = chunkSize;
      if (pos < offset) {
        length -= (offset - pos);
        pos = offset;
      }
      channel.position(pos);
      buf.limit(length);
      channel.read(buf);
      buf.flip();
      channel.position(pos + noBytes);
      channel.write(buf);
      buf.clear();
    }
    size += noBytes;
  }

  @Override
  public void close() throws IOException {
    channel.close();
  }

  @Override
  public long size() {
    return size;
  }
}
