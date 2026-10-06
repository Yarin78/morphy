package se.yarin.morphy.positions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * An open position index: the keys of the positions several games reached, their offsets, the
 * directory of the single-game positions and the facts of the games are in memory (some 650 MB for
 * a Megabase); the rest is read from the files as positions are looked up. Lookups can be made
 * from several threads at once.
 */
public final class PositionIndex implements AutoCloseable {
  private final @NotNull Path directory;
  private final @NotNull IndexMeta meta;
  private final long @NotNull [] sharedKeys;
  private final long @NotNull [] sharedOffsets;
  private final int @NotNull [] singleDirectory;
  private final @NotNull GameFactsTable facts;
  private final @NotNull FileChannel sharedData;
  private final @NotNull FileChannel singleData;

  private PositionIndex(
      Path directory,
      IndexMeta meta,
      long[] sharedKeys,
      long[] sharedOffsets,
      int[] singleDirectory,
      GameFactsTable facts,
      FileChannel sharedData,
      FileChannel singleData) {
    this.directory = directory;
    this.meta = meta;
    this.sharedKeys = sharedKeys;
    this.sharedOffsets = sharedOffsets;
    this.singleDirectory = singleDirectory;
    this.facts = facts;
    this.sharedData = sharedData;
    this.singleData = singleData;
  }

  /**
   * Opens an index.
   *
   * @throws IOException if there is no index in the directory, or it can't be read
   */
  public static @NotNull PositionIndex open(@NotNull Path directory) throws IOException {
    IndexMeta meta = IndexMeta.read(directory.resolve(IndexFiles.META));
    long[] keys = IndexFiles.readLongs(directory.resolve(IndexFiles.SHARED_KEYS));
    long[] offsets = IndexFiles.readLongs(directory.resolve(IndexFiles.SHARED_OFFSETS));
    int[] singleDirectory = IndexFiles.readInts(directory.resolve(IndexFiles.SINGLE_DIRECTORY));
    GameFactsTable facts = GameFactsTable.read(directory.resolve(IndexFiles.FACTS));
    FileChannel shared =
        FileChannel.open(directory.resolve(IndexFiles.SHARED_DATA), StandardOpenOption.READ);
    FileChannel single;
    try {
      single = FileChannel.open(directory.resolve(IndexFiles.SINGLE_DATA), StandardOpenOption.READ);
    } catch (IOException e) {
      shared.close();
      throw e;
    }
    return new PositionIndex(directory, meta, keys, offsets, singleDirectory, facts, shared, single);
  }

  public @NotNull Path directory() {
    return directory;
  }

  public @NotNull IndexMeta meta() {
    return meta;
  }

  public @NotNull GameFactsTable facts() {
    return facts;
  }

  /** Whether the database has changed since the index was built. */
  public boolean isStale(@NotNull DatabaseIdentity database) {
    return !meta.database().equals(database);
  }

  /** What the index knows of a position, by its {@link PositionKeys#hash}. */
  public @NotNull Lookup lookup(long hash) {
    int i = indexOf(hash);
    if (i >= 0) {
      return new Lookup.Shared(readShared(i));
    }
    return new Lookup.SingleCandidates(readSingle(hash));
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

  private List<MoveGroup> readShared(int i) {
    long offset = sharedOffsets[i];
    ByteBuffer buf = read(sharedData, offset, (int) (sharedOffsets[i + 1] - offset));
    int groups = (int) Bytes.getVar(buf);
    List<MoveGroup> result = new ArrayList<>(groups);
    for (int g = 0; g < groups; g++) {
      int move = buf.getShort() & 0xFFFF;
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
    return result;
  }

  private int[] readSingle(long hash) {
    int bucket = (int) (hash >>> (64 - IndexFiles.DIRECTORY_BITS));
    int from = singleDirectory[bucket], to = singleDirectory[bucket + 1];
    if (from == to) {
      return new int[0];
    }
    int entry = 2 + meta.gameIdBytes();
    ByteBuffer buf = read(singleData, (long) from * entry, (to - from) * entry);
    int check = (int) ((hash >>> 24) & 0xFFFF);
    int[] found = new int[to - from];
    int n = 0;
    for (int k = 0; k < to - from; k++) {
      int c = buf.getShort() & 0xFFFF;
      int id = meta.gameIdBytes() == 4 ? buf.get() & 0xFF : 0;
      id = (id << 16) | (buf.getShort() & 0xFFFF);
      id = (id << 8) | (buf.get() & 0xFF);
      if (c == check) {
        found[n++] = id;
      }
    }
    return Arrays.copyOf(found, n);
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
   * The statistics of a move's games: those stored in the index, or worked out from the facts of
   * the games.
   *
   * @param whiteToMove whether White is to move in the position
   */
  public @NotNull MoveStats stats(@NotNull MoveGroup group, boolean whiteToMove) {
    return group.stats() != null
        ? group.stats()
        : MoveStats.of(group.gameIds(), facts, whiteToMove, meta.recentSince());
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
