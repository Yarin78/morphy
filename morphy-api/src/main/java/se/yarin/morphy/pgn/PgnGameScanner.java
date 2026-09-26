package se.yarin.morphy.pgn;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

/**
 * Finds where the games start in a PGN file, without parsing them.
 *
 * <p>Works on bytes, since the offsets are byte offsets and the file may mix encodings. A game
 * starts at a line that begins with {@code [}, when the previous game has got past its tags (or
 * there is no previous game). Braces comments, which can run over several lines and may contain
 * lines beginning with {@code [}, and {@code ;} comments and {@code %} escape lines are skipped, so
 * they never start a game. A UTF-8 byte order mark at the start of the file belongs to no game.
 */
final class PgnGameScanner {

  private static final int BUFFER_SIZE = 1 << 16;

  private PgnGameScanner() {}

  /**
   * Scans a whole file.
   *
   * @return the byte offset of the start of every game, followed by the size of the file
   */
  static long @NotNull [] scan(@NotNull InputStream in) throws IOException {
    byte[] buf = new byte[BUFFER_SIZE];
    long[] offsets = new long[1024];
    int count = 0;

    long pos = 0;
    boolean lineStart = true;
    boolean skipLine = false; // the rest of this line is a tag line, a % line or a ; comment
    boolean inBrace = false;
    boolean inTags = false; // the current game hasn't got to its movetext yet
    boolean first = true;

    int len;
    while ((len = in.readNBytes(buf, 0, buf.length)) > 0) {
      int i = 0;
      if (first) {
        first = false;
        if (len >= 3 && (buf[0] & 0xFF) == 0xEF && (buf[1] & 0xFF) == 0xBB && (buf[2] & 0xFF) == 0xBF) {
          i = 3;
          pos = 3;
        }
      }
      for (; i < len; i++, pos++) {
        int b = buf[i] & 0xFF;
        if (skipLine) {
          if (b == '\n') {
            skipLine = false;
            lineStart = true;
          }
          continue;
        }
        if (lineStart) {
          lineStart = false;
          if (!inBrace) {
            if (b == '[') {
              if (!inTags) {
                if (count == offsets.length) {
                  offsets = Arrays.copyOf(offsets, count * 2);
                }
                offsets[count++] = pos;
                inTags = true;
              }
              skipLine = true;
              continue;
            }
            if (b == '%') {
              skipLine = true;
              continue;
            }
          }
        }
        if (b == '\n') {
          lineStart = true;
        } else if (inBrace) {
          if (b == '}') {
            inBrace = false;
          }
        } else if (b == '{') {
          inBrace = true;
          inTags = false;
        } else if (b == ';') {
          skipLine = true;
        } else if (b > ' ') {
          inTags = false;
        }
      }
    }

    offsets = Arrays.copyOf(offsets, count + 1);
    offsets[count] = pos;
    return offsets;
  }
}
