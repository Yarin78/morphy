package se.yarin.morphy.pgn;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code .pgi} index file next to a {@code .pgn} file, as ChessBase writes it: little-endian
 * 64-bit values, the first being the number of offsets that follow. The offsets are the byte
 * position of the start of every game in the PGN file, followed by the size of the PGN file. There
 * is thus one more offset than there are games, and game {@code i} spans {@code offsets[i]} up to
 * {@code offsets[i + 1]}.
 *
 * <p>The index says nothing about which version of the PGN file it belongs to, so it is checked
 * against the file: the last offset must be the size of the file, the offsets must increase and
 * a sample of them must point at a {@code [}. An index that fails is ignored and the file is scanned
 * instead.
 */
final class PgnIndex {
  private static final Logger log = LoggerFactory.getLogger(PgnIndex.class);

  private static final int SAMPLES = 16;

  private PgnIndex() {}

  /** The index file of a PGN file: the same name, with the extension {@code .pgi}. */
  static @NotNull Path indexPath(@NotNull Path pgn) {
    String name = pgn.getFileName().toString();
    int dot = name.lastIndexOf('.');
    String base = dot < 0 ? name : name.substring(0, dot);
    return pgn.resolveSibling(base + ".pgi");
  }

  /**
   * Gets the offsets of the games in a PGN file, from its index if that is valid, otherwise by
   * scanning the file.
   *
   * @param writeIndex whether to write a new index file after having scanned; if the file has
   *     changed, a stale index is replaced
   * @return the start of every game, followed by the size of the file
   */
  static long @NotNull [] load(@NotNull Path pgn, boolean writeIndex) throws IOException {
    Path pgi = indexPath(pgn);
    long size = Files.size(pgn);
    if (Files.exists(pgi)) {
      try {
        long[] offsets = read(pgi);
        if (offsets != null && isValid(offsets, pgn, size)) {
          return offsets;
        }
        log.info("The index {} doesn't match {}, scanning the file", pgi.getFileName(), pgn.getFileName());
      } catch (IOException e) {
        log.warn("Can't read the index {}, scanning the file", pgi.getFileName(), e);
      }
    }
    long[] offsets;
    try (InputStream in = new BufferedInputStream(Files.newInputStream(pgn), 1 << 16)) {
      offsets = PgnGameScanner.scan(in);
    }
    if (writeIndex) {
      tryWrite(pgi, offsets);
    }
    return offsets;
  }

  /**
   * Writes an index file, unless that fails. The index is only a cache, so the database can be used
   * without it; it is rebuilt when it is next needed.
   */
  static void tryWrite(@NotNull Path pgi, long @NotNull [] offsets) {
    try {
      write(pgi, offsets);
    } catch (IOException e) {
      log.warn("Can't write the index {}", pgi.getFileName(), e);
    }
  }

  /** Reads an index file, or returns null if it isn't shaped like one. */
  static long @Nullable [] read(@NotNull Path pgi) throws IOException {
    long fileSize = Files.size(pgi);
    if (fileSize < 8 || fileSize % 8 != 0) {
      return null;
    }
    ByteBuffer buf = ByteBuffer.wrap(Files.readAllBytes(pgi)).order(ByteOrder.LITTLE_ENDIAN);
    long count = buf.getLong();
    if (count != fileSize / 8 - 1 || count < 1) {
      return null;
    }
    long[] offsets = new long[(int) count];
    for (int i = 0; i < offsets.length; i++) {
      offsets[i] = buf.getLong();
    }
    return offsets;
  }

  /** Writes an index file, all or nothing. */
  static void write(@NotNull Path pgi, long @NotNull [] offsets) throws IOException {
    ByteBuffer buf = ByteBuffer.allocate((offsets.length + 1) * 8).order(ByteOrder.LITTLE_ENDIAN);
    buf.putLong(offsets.length);
    for (long offset : offsets) {
      buf.putLong(offset);
    }
    Path tmp = pgi.resolveSibling(pgi.getFileName() + ".tmp");
    Files.write(tmp, buf.array());
    Files.move(tmp, pgi, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  /** Checks offsets against the PGN file they are meant for. */
  static boolean isValid(long @NotNull [] offsets, @NotNull Path pgn, long pgnSize)
      throws IOException {
    int games = offsets.length - 1;
    if (offsets[games] != pgnSize) {
      return false;
    }
    for (int i = 0; i < games; i++) {
      if (offsets[i] < 0 || offsets[i] >= offsets[i + 1]) {
        return false;
      }
    }
    // Each sampled game must start with a tag
    try (FileChannel channel = FileChannel.open(pgn, StandardOpenOption.READ)) {
      ByteBuffer one = ByteBuffer.allocate(1);
      int step = Math.max(1, games / SAMPLES);
      for (int i = 0; i < games; i += step) {
        if (!startsWithTag(channel, one, offsets[i])) {
          return false;
        }
      }
      return games == 0 || startsWithTag(channel, one, offsets[games - 1]);
    }
  }

  private static boolean startsWithTag(FileChannel channel, ByteBuffer one, long position)
      throws IOException {
    one.clear();
    return channel.read(one, position) == 1 && one.get(0) == '[';
  }
}
