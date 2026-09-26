package se.yarin.morphy.pgn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import org.jetbrains.annotations.NotNull;

/**
 * A PGN file, seen as a numbered sequence of games. Keeps the offset of each game, from the {@code
 * .pgi} index or a scan, and reads games straight from the file.
 *
 * <p>Games are read as UTF-8, or as Windows-1252 if the bytes of a game aren't valid UTF-8, which
 * is what older ChessBase exports are; the encoding is decided game by game since a file can mix
 * them. New games are written as UTF-8.
 *
 * <p>Not thread safe: the owner serializes writes against everything else. Reads may run
 * concurrently with each other.
 *
 * <p>A file is only ever changed by appending a game, or by writing a whole new file that copies
 * every other game byte for byte. The index is written after the file, so a crash between the two
 * leaves an index that no longer matches, which is detected the next time the file is opened.
 */
final class PgnFile implements AutoCloseable {
  private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

  private final @NotNull Path path;
  private final boolean writable;
  private long @NotNull [] offsets;
  private @NotNull FileChannel channel;

  private PgnFile(@NotNull Path path, boolean writable, long @NotNull [] offsets, @NotNull FileChannel channel) {
    this.path = path;
    this.writable = writable;
    this.offsets = offsets;
    this.channel = channel;
  }

  /**
   * Opens a PGN file.
   *
   * @param writable whether the file may be changed; if so a missing or stale index is rewritten
   */
  static @NotNull PgnFile open(@NotNull Path path, boolean writable) throws IOException {
    long[] offsets = PgnIndex.load(path, writable);
    return new PgnFile(path, writable, offsets, FileChannel.open(path, StandardOpenOption.READ));
  }

  /** Creates an empty PGN file and its index. */
  static @NotNull PgnFile create(@NotNull Path path) throws IOException {
    Files.createFile(path);
    PgnIndex.write(PgnIndex.indexPath(path), new long[] {0});
    return open(path, true);
  }

  int gameCount() {
    return offsets.length - 1;
  }

  // ── Reading ──────────────────────────────────────────────────────────────

  /** The bytes of a game, including any blank lines after it. */
  byte @NotNull [] rawGame(int index) throws IOException {
    checkIndex(index);
    long from = offsets[index];
    long length = offsets[index + 1] - from;
    ByteBuffer buf = ByteBuffer.allocate((int) length);
    while (buf.hasRemaining()) {
      if (channel.read(buf, from + buf.position()) < 0) {
        throw new IOException("Unexpected end of " + path.getFileName());
      }
    }
    return buf.array();
  }

  /** The text of a game. */
  @NotNull String gameText(int index) throws IOException {
    return decode(rawGame(index));
  }

  static @NotNull String decode(byte @NotNull [] bytes) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException e) {
      return new String(bytes, WINDOWS_1252);
    }
  }

  private void checkIndex(int index) {
    if (index < 0 || index >= gameCount()) {
      throw new IndexOutOfBoundsException("No game " + index + " in " + path.getFileName());
    }
  }

  // ── Writing ──────────────────────────────────────────────────────────────

  /** The line ending the file uses, judging from its first game. */
  @NotNull String lineEnding() throws IOException {
    if (gameCount() == 0) {
      return "\n";
    }
    byte[] first = rawGame(0);
    for (int i = 0; i < first.length; i++) {
      if (first[i] == '\n') {
        return i > 0 && first[i - 1] == '\r' ? "\r\n" : "\n";
      }
    }
    return "\n";
  }

  /**
   * Adds a game to the end of the file.
   *
   * @param game the text of the game, ending in a line ending; a blank line is added after it
   */
  void append(@NotNull String game, @NotNull String lineEnding) throws IOException {
    requireWritable();
    long size = offsets[gameCount()];

    // Whatever came before must end in a blank line for the new game not to run into it
    StringBuilder text = new StringBuilder();
    int missing = 2 - trailingLineEndings(size);
    if (size > 0) {
      text.append(lineEnding.repeat(Math.max(0, missing)));
    }
    long start = size + text.length();
    text.append(game).append(lineEnding);
    byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_8);
    // The offset is in bytes, and the padding is ASCII
    long end = size + bytes.length;

    try (FileChannel out = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
      ByteBuffer buf = ByteBuffer.wrap(bytes);
      while (buf.hasRemaining()) {
        out.write(buf);
      }
      out.force(true);
    }

    long[] updated = new long[offsets.length + 1];
    System.arraycopy(offsets, 0, updated, 0, gameCount());
    updated[gameCount()] = start;
    updated[gameCount() + 1] = end;
    offsets = updated;
    PgnIndex.tryWrite(PgnIndex.indexPath(path), offsets);
  }

  /** The number of line endings at the very end of the file, at most two. */
  private int trailingLineEndings(long size) throws IOException {
    int tail = (int) Math.min(size, 4);
    ByteBuffer buf = ByteBuffer.allocate(tail);
    while (buf.hasRemaining()) {
      if (channel.read(buf, size - tail + buf.position()) < 0) {
        break;
      }
    }
    int count = 0;
    for (int i = tail - 1; i >= 0 && count < 2; i--) {
      byte b = buf.array()[i];
      if (b == '\n') {
        count++;
      } else if (b != '\r') {
        break;
      }
    }
    return count;
  }

  /**
   * Replaces a game. The file is written anew, next to the old one, and then moved into place;
   * every other game is copied as it is.
   *
   * @param game the text of the new game, ending in a line ending; a blank line is added after it
   */
  void replace(int index, @NotNull String game, @NotNull String lineEnding) throws IOException {
    requireWritable();
    checkIndex(index);
    byte[] replacement = (game + lineEnding).getBytes(StandardCharsets.UTF_8);

    Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
    long[] updated = new long[offsets.length];
    try (FileChannel out =
        FileChannel.open(
            tmp,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE)) {
      // Anything before the first game, such as a byte order mark
      copy(0, offsets[0], out);
      for (int i = 0; i < gameCount(); i++) {
        updated[i] = out.position();
        if (i == index) {
          ByteBuffer buf = ByteBuffer.wrap(replacement);
          while (buf.hasRemaining()) {
            out.write(buf);
          }
        } else {
          copy(offsets[i], offsets[i + 1], out);
        }
      }
      long end = offsets[gameCount()];
      long total = Files.size(path);
      // Anything after the last game
      copy(end, total, out);
      updated[gameCount()] = out.position();
      out.force(true);
    } catch (IOException | RuntimeException e) {
      Files.deleteIfExists(tmp);
      throw e;
    }

    channel.close();
    try {
      Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      offsets = updated;
    } finally {
      // Read from whichever file is in place, the new one, or the old one if the move failed
      channel = FileChannel.open(path, StandardOpenOption.READ);
    }
    PgnIndex.tryWrite(PgnIndex.indexPath(path), offsets);
  }

  private void copy(long from, long to, FileChannel out) throws IOException {
    long position = from;
    while (position < to) {
      long n = channel.transferTo(position, to - position, out);
      if (n <= 0) {
        throw new IOException("Unexpected end of " + path.getFileName());
      }
      position += n;
    }
  }

  private void requireWritable() {
    if (!writable) {
      throw new UnsupportedOperationException("The database is read-only: " + path.getFileName());
    }
  }

  @Override
  public void close() throws IOException {
    channel.close();
  }
}
