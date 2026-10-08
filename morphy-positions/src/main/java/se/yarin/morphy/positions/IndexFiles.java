package se.yarin.morphy.positions;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.jetbrains.annotations.NotNull;

/**
 * The files of a position index, in a directory, by default next to the database (see {@link
 * #indexDirectoryOf}): the manifest, the facts of the games, and a directory per segment. Numbers
 * of fixed width are big-endian.
 */
public final class IndexFiles {
  private IndexFiles() {}

  /** The manifest: what the index was built from, and its segments; see {@link IndexMeta}. */
  public static final String MANIFEST = "index.properties";

  // A segment's files, in its directory
  static final String SEGMENT_META = "segment.properties";
  static final String SHARED_KEYS = "shared.keys";
  static final String SHARED_OFFSETS = "shared.offsets";
  static final String SHARED_DATA = "shared.data";
  static final String SINGLE_DIRECTORY = "single.dir";
  static final String SINGLE_DATA = "single.data";
  static final String SUPERSEDES = "supersedes.bin";

  /**
   * The move code of the games that ended in a position: no move. The other codes are {@link
   * se.yarin.chess.MoveCode}s, of 15 bits, among which 0 (a1 to a1) is no move.
   */
  static final int GAME_ENDED = 0;

  /**
   * A move as stored: its code in the low 15 bits, and above them whether White played it (is to
   * move in the position).
   */
  static int moveField(int moveCode, boolean whiteToMove) {
    return (whiteToMove ? 0x8000 : 0) | moveCode;
  }

  static int codeOf(int moveField) {
    return moveField & 0x7FFF;
  }

  static boolean whiteToMoveOf(int moveField) {
    return (moveField & 0x8000) != 0;
  }

  /** The default index directory of a database file: {@code Mega.2cbh} has {@code Mega.positions}. */
  public static @NotNull Path indexDirectoryOf(@NotNull Path databaseFile) {
    return databaseFile.resolveSibling(baseName(databaseFile) + ".positions");
  }

  /**
   * The default directory of a named index of a database, as a database may have several (with
   * different filters): index {@code classical} of {@code Mega.2cbh} is in {@code
   * Mega.classical.positions}.
   */
  public static @NotNull Path indexDirectoryOf(@NotNull Path databaseFile, @NotNull String indexId) {
    return databaseFile.resolveSibling(baseName(databaseFile) + "." + indexId + ".positions");
  }

  private static String baseName(Path file) {
    String name = file.getFileName().toString();
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  static void writeFully(@NotNull FileChannel channel, @NotNull ByteBuffer buf) throws IOException {
    while (buf.hasRemaining()) {
      channel.write(buf);
    }
  }

  static void readFully(@NotNull FileChannel channel, @NotNull ByteBuffer buf, long position)
      throws IOException {
    while (buf.hasRemaining()) {
      int n = channel.read(buf, position + buf.position());
      if (n < 0) {
        throw new IOException("Unexpected end of an index file");
      }
    }
    buf.flip();
  }

  static long @NotNull [] readLongs(@NotNull Path file) throws IOException {
    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
      long[] values = new long[Math.toIntExact(channel.size() / 8)];
      ByteBuffer buf = ByteBuffer.allocate(1 << 20);
      int i = 0;
      long position = 0;
      while (i < values.length) {
        buf.clear().limit((int) Math.min(buf.capacity(), (values.length - i) * 8L));
        readFully(channel, buf, position);
        position += buf.limit();
        while (buf.hasRemaining()) {
          values[i++] = buf.getLong();
        }
      }
      return values;
    }
  }

  static int @NotNull [] readInts(@NotNull Path file) throws IOException {
    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
      int[] values = new int[Math.toIntExact(channel.size() / 4)];
      ByteBuffer buf = ByteBuffer.allocate(1 << 20);
      int i = 0;
      long position = 0;
      while (i < values.length) {
        buf.clear().limit((int) Math.min(buf.capacity(), (values.length - i) * 4L));
        readFully(channel, buf, position);
        position += buf.limit();
        while (buf.hasRemaining()) {
          values[i++] = buf.getInt();
        }
      }
      return values;
    }
  }

  /** Deletes a directory and everything in it, if it exists. */
  static void deleteDirectory(@NotNull Path dir) throws IOException {
    if (!Files.isDirectory(dir)) {
      return;
    }
    try (var files = Files.list(dir)) {
      for (Path f : files.toList()) {
        if (Files.isDirectory(f)) {
          deleteDirectory(f);
        } else {
          Files.delete(f);
        }
      }
    }
    Files.delete(dir);
  }

  static void writeLongs(@NotNull Path file, long @NotNull [] values) throws IOException {
    try (FileChannel channel =
        FileChannel.open(
            file,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE)) {
      ByteBuffer buf = ByteBuffer.allocate(1 << 20);
      for (long v : values) {
        if (!buf.hasRemaining()) {
          writeFully(channel, buf.flip());
          buf.clear();
        }
        buf.putLong(v);
      }
      writeFully(channel, buf.flip());
    }
  }

  static void writeInts(@NotNull Path file, int @NotNull [] values) throws IOException {
    try (FileChannel channel =
        FileChannel.open(
            file,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE)) {
      ByteBuffer buf = ByteBuffer.allocate(1 << 20);
      for (int v : values) {
        if (!buf.hasRemaining()) {
          writeFully(channel, buf.flip());
          buf.clear();
        }
        buf.putInt(v);
      }
      writeFully(channel, buf.flip());
    }
  }
}
