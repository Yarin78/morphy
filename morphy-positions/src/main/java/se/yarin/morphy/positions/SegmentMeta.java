package se.yarin.morphy.positions;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;

/**
 * What a segment holds and how its files are laid out: kept in its {@code segment.properties}.
 *
 * @param sharedPositions the positions several of its games reached
 * @param singlePositions the positions one of its games reached
 * @param directoryBits the top bits of a hash {@code single.dir} is by; the rest are stored
 * @param gameIdBytes the bytes of a game id in {@code single.data}
 */
record SegmentMeta(
    long sharedPositions, long singlePositions, int directoryBits, int gameIdBytes) {

  /** The bytes of the hash in an entry of {@code single.data}: the bits below the directory's. */
  int hashBytes() {
    return (64 - directoryBits + 7) / 8;
  }

  /** The bytes of an entry of {@code single.data}: the hash, the move and the game. */
  int singleEntryBytes() {
    return hashBytes() + 2 + gameIdBytes;
  }

  void write(@NotNull Path file) throws IOException {
    Properties p = new Properties();
    p.setProperty("sharedPositions", String.valueOf(sharedPositions));
    p.setProperty("singlePositions", String.valueOf(singlePositions));
    p.setProperty("directoryBits", String.valueOf(directoryBits));
    p.setProperty("gameIdBytes", String.valueOf(gameIdBytes));
    try (Writer out = Files.newBufferedWriter(file)) {
      p.store(out, "Morphy position index segment");
    }
  }

  static @NotNull SegmentMeta read(@NotNull Path file) throws IOException {
    Properties p = new Properties();
    try (Reader in = Files.newBufferedReader(file)) {
      p.load(in);
    }
    return new SegmentMeta(
        Long.parseLong(p.getProperty("sharedPositions")),
        Long.parseLong(p.getProperty("singlePositions")),
        Integer.parseInt(p.getProperty("directoryBits")),
        Integer.parseInt(p.getProperty("gameIdBytes")));
  }
}
