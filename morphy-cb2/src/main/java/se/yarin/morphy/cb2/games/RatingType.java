package se.yarin.morphy.cb2.games;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.NotNull;

/**
 * The 14 bytes after each elo of a game record, describing the rating the elo belongs to.
 *
 * @param kindAndTimeControl the kind in the bottom three bits (1 international, 2 national, 3
 *     server) and the time control above them (0 normal, 1 bullet, 2 blitz, 3 rapid, 4
 *     correspondence)
 * @param list the rating list, e.g. 1 FIDE standard, 100 any national rating
 * @param nation the nation of a national rating, 196 for most servers, otherwise 0
 * @param name the name, at most 8 bytes, e.g. {@code FIDE}; empty for a national rating
 */
public record RatingType(int kindAndTimeControl, int list, int nation, @NotNull String name) {

  public static final int SIZE = 14;
  private static final int NAME_SIZE = 8;

  /** No rating type, as stored with an elo of 0. */
  public static final RatingType NONE = new RatingType(0, 0, 0, "");

  /** The rating type of an international (FIDE) rating at a normal time control. */
  public static final RatingType FIDE = new RatingType(1, 1, 0, "FIDE");

  public int kind() {
    return kindAndTimeControl & 7;
  }

  public int timeControl() {
    return kindAndTimeControl >> 3;
  }

  public static @NotNull RatingType read(@NotNull ByteBuffer buf) {
    int kind = buf.getShort();
    int list = buf.getShort();
    int nation = buf.getShort();
    byte[] name = new byte[NAME_SIZE];
    buf.get(name);
    int length = 0;
    while (length < NAME_SIZE && name[length] != 0) {
      length++;
    }
    return new RatingType(kind, list, nation, new String(name, 0, length, StandardCharsets.ISO_8859_1));
  }

  public void write(@NotNull ByteBuffer buf) {
    buf.putShort((short) kindAndTimeControl);
    buf.putShort((short) list);
    buf.putShort((short) nation);
    byte[] bytes = name.getBytes(StandardCharsets.ISO_8859_1);
    byte[] field = new byte[NAME_SIZE];
    System.arraycopy(bytes, 0, field, 0, Math.min(bytes.length, NAME_SIZE));
    buf.put(field);
  }
}
