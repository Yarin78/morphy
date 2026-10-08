package se.yarin.morphy.positions;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An open segment, as {@link SegmentWriter} wrote it: the keys and offsets of its shared positions
 * and its single-game directory in memory, the rest read as positions are looked up. Lookups can
 * be made from several threads at once.
 */
final class SegmentReader implements AutoCloseable {
  private final @NotNull Path directory;
  private final @NotNull SegmentMeta meta;
  private final long @NotNull [] sharedKeys;
  private final long @NotNull [] sharedOffsets;
  private final int @NotNull [] singleDirectory;
  private final int @NotNull [] supersedes;
  private final @NotNull FileChannel sharedData;
  private final @NotNull FileChannel singleData;

  private SegmentReader(
      Path directory,
      SegmentMeta meta,
      long[] sharedKeys,
      long[] sharedOffsets,
      int[] singleDirectory,
      int[] supersedes,
      FileChannel sharedData,
      FileChannel singleData) {
    this.directory = directory;
    this.meta = meta;
    this.sharedKeys = sharedKeys;
    this.sharedOffsets = sharedOffsets;
    this.singleDirectory = singleDirectory;
    this.supersedes = supersedes;
    this.sharedData = sharedData;
    this.singleData = singleData;
  }

  static @NotNull SegmentReader open(@NotNull Path directory) throws IOException {
    SegmentMeta meta = SegmentMeta.read(directory.resolve(IndexFiles.SEGMENT_META));
    long[] keys = IndexFiles.readLongs(directory.resolve(IndexFiles.SHARED_KEYS));
    long[] offsets = IndexFiles.readLongs(directory.resolve(IndexFiles.SHARED_OFFSETS));
    int[] singleDirectory = IndexFiles.readInts(directory.resolve(IndexFiles.SINGLE_DIRECTORY));
    Path supersedesFile = directory.resolve(IndexFiles.SUPERSEDES);
    int[] supersedes =
        Files.exists(supersedesFile) ? IndexFiles.readInts(supersedesFile) : new int[0];
    FileChannel shared =
        FileChannel.open(directory.resolve(IndexFiles.SHARED_DATA), StandardOpenOption.READ);
    FileChannel single;
    try {
      single = FileChannel.open(directory.resolve(IndexFiles.SINGLE_DATA), StandardOpenOption.READ);
    } catch (IOException e) {
      shared.close();
      throw e;
    }
    return new SegmentReader(
        directory, meta, keys, offsets, singleDirectory, supersedes, shared, single);
  }

  @NotNull
  Path directory() {
    return directory;
  }

  @NotNull
  SegmentMeta meta() {
    return meta;
  }

  /** The games whose entries in the segments before this one are void, in id order. */
  int @NotNull [] supersedes() {
    return supersedes;
  }

  /** A position of the segment by its hash, or null if none of its games reached it. */
  @Nullable
  PositionEntry lookup(long hash) {
    int i = indexOf(hash);
    if (i >= 0) {
      long offset = sharedOffsets[i];
      return readShared(hash, read(sharedData, offset, (int) (sharedOffsets[i + 1] - offset)));
    }
    return lookupSingle(hash);
  }

  /** Where a key is among the shared keys, which are in unsigned order, or -1. */
  private int indexOf(long hash) {
    int low = 0, high = sharedKeys.length - 1;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      int c = Long.compareUnsigned(sharedKeys[mid], hash);
      if (c < 0) {
        low = mid + 1;
      } else if (c > 0) {
        high = mid - 1;
      } else {
        return mid;
      }
    }
    return -1;
  }

  private @Nullable PositionEntry lookupSingle(long hash) {
    int bucket = (int) (hash >>> (64 - meta.directoryBits()));
    int from = singleDirectory[bucket], to = singleDirectory[bucket + 1];
    if (from == to) {
      return null;
    }
    int entry = meta.singleEntryBytes();
    ByteBuffer buf = read(singleData, (long) from * entry, (to - from) * entry);
    for (int k = from; k < to; k++) {
      PositionEntry single = readSingle(buf, bucket);
      if (single.hash() == hash) {
        return single;
      }
    }
    return null;
  }

  /** The single-game entry at a buffer's position, its hash completed by the directory bucket's. */
  private PositionEntry readSingle(ByteBuffer buf, long bucket) {
    long low = 0;
    for (int b = 0; b < meta.hashBytes(); b++) {
      low = (low << 8) | (buf.get() & 0xFF);
    }
    int bits = 64 - meta.directoryBits();
    long hash = (bucket << bits) | (low & ((1L << bits) - 1));
    int move = buf.getShort() & 0xFFFF;
    int game = 0;
    for (int b = 0; b < meta.gameIdBytes(); b++) {
      game = (game << 8) | (buf.get() & 0xFF);
    }
    return new PositionEntry(
        hash,
        IndexFiles.whiteToMoveOf(move),
        List.of(new MoveGroup(IndexFiles.codeOf(move), new int[] {game}, null)));
  }

  private static PositionEntry readShared(long hash, ByteBuffer buf) {
    long header = Bytes.getVar(buf);
    int groups = (int) (header >>> 1);
    List<MoveGroup> result = new ArrayList<>(groups);
    for (int g = 0; g < groups; g++) {
      int move = IndexFiles.codeOf(buf.getShort() & 0xFFFF);
      int n = (int) Bytes.getVar(buf);
      boolean hasStats = buf.get() != 0;
      MoveStats stats = hasStats ? MoveStats.read(buf) : null;
      int[] ids = new int[n];
      int previous = 0;
      for (int k = 0; k < n; k++) {
        previous += (int) Bytes.getVar(buf);
        ids[k] = previous;
      }
      result.add(new MoveGroup(move, ids, stats));
    }
    return new PositionEntry(hash, (header & 1) != 0, result);
  }

  private static ByteBuffer read(FileChannel channel, long position, int length) {
    ByteBuffer buf = ByteBuffer.allocate(length);
    try {
      IndexFiles.readFully(channel, buf, position);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return buf;
  }

  /**
   * Every position of the segment in hash order, the shared and single-game ones together, its
   * files read through from the start.
   */
  @NotNull
  Positions positions() throws IOException {
    return new Positions();
  }

  /** The positions of a segment in hash order; see {@link #positions()}. */
  final class Positions implements AutoCloseable {
    private final DataInputStream shared;
    private final InputStream single;
    private final ByteBuffer entry = ByteBuffer.allocate(meta.singleEntryBytes());
    private int nextShared;
    private int nextSingle;
    private int bucket;
    private @Nullable PositionEntry pendingShared, pendingSingle;

    private Positions() throws IOException {
      shared =
          new DataInputStream(
              new BufferedInputStream(
                  Files.newInputStream(directory.resolve(IndexFiles.SHARED_DATA)), 1 << 20));
      single =
          new BufferedInputStream(
              Files.newInputStream(directory.resolve(IndexFiles.SINGLE_DATA)), 1 << 20);
    }

    /** The next position, or null after the last. */
    @Nullable
    PositionEntry next() throws IOException {
      if (pendingShared == null && nextShared < sharedKeys.length) {
        int length = (int) (sharedOffsets[nextShared + 1] - sharedOffsets[nextShared]);
        byte[] bytes = new byte[length];
        shared.readFully(bytes);
        pendingShared = readShared(sharedKeys[nextShared++], ByteBuffer.wrap(bytes));
      }
      if (pendingSingle == null && nextSingle < singleDirectory[singleDirectory.length - 1]) {
        while (singleDirectory[bucket + 1] <= nextSingle) {
          bucket++;
        }
        if (single.readNBytes(entry.array(), 0, entry.capacity()) != entry.capacity()) {
          throw new IOException("Unexpected end of " + directory.resolve(IndexFiles.SINGLE_DATA));
        }
        pendingSingle = readSingle(entry.clear(), bucket);
        nextSingle++;
      }
      PositionEntry next;
      if (pendingShared == null
          || (pendingSingle != null
              && Long.compareUnsigned(pendingSingle.hash(), pendingShared.hash()) < 0)) {
        next = pendingSingle;
        pendingSingle = null;
      } else {
        next = pendingShared;
        pendingShared = null;
      }
      return next;
    }

    @Override
    public void close() throws IOException {
      try {
        shared.close();
      } finally {
        single.close();
      }
    }
  }

  @Override
  public void close() throws IOException {
    try {
      sharedData.close();
    } finally {
      singleData.close();
    }
  }
}
