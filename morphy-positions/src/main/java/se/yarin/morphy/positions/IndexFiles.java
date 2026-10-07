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
 * #indexDirectoryOf}). Numbers of fixed width are big-endian.
 */
public final class IndexFiles {
  private IndexFiles() {}

  static final String META = "meta.properties";
  static final String FACTS = "facts.bin";
  static final String SHARED_KEYS = "shared.keys";
  static final String SHARED_OFFSETS = "shared.offsets";
  static final String SHARED_DATA = "shared.data";
  static final String SINGLE_DIRECTORY = "single.dir";
  static final String SINGLE_DATA = "single.data";

  /**
   * The move code of the games that ended in a position, in a shared position's record: no move.
   * The other codes are {@link se.yarin.chess.MoveCode}s, which fit in 15 bits.
   */
  static final int GAME_ENDED = 0xFFFF;

  /** The top bits of a hash the directory of the single-game positions is by. */
  static final int DIRECTORY_BITS = 24;

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

  /** Deletes a directory and the files in it, if it exists. */
  static void deleteDirectory(@NotNull Path dir) throws IOException {
    if (!Files.isDirectory(dir)) {
      return;
    }
    try (var files = Files.list(dir)) {
      for (Path f : files.toList()) {
        Files.delete(f);
      }
    }
    Files.delete(dir);
  }
}
