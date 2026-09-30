package se.yarin.morphy.cb2.entities;

import java.nio.ByteBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A source.
 *
 * @param title the title
 * @param publisher the publisher
 * @param publicationDate the publication date, packed as in {@link
 *     se.yarin.morphy.cb2.games.Dates}
 * @param date the date
 * @param version the version
 * @param quality 0 unset, 1 high, 2 medium, 3 low
 */
public record Source(
    @NotNull String title,
    @NotNull String publisher,
    int publicationDate,
    int date,
    int version,
    int quality)
    implements Entity {

  /** The qualities by their stored numbers, 0 being unset. */
  private static final String[] QUALITIES = {"UNSET", "HIGH", "MEDIUM", "LOW"};

  public static @NotNull Source of(@NotNull String title) {
    return new Source(title, "", 0, 0, 0, 0);
  }

  /** The stored number of a quality named HIGH, MEDIUM or LOW; 0, unset, for anything else. */
  public static int quality(@Nullable String name) {
    for (int i = 1; i < QUALITIES.length; i++) {
      if (QUALITIES[i].equalsIgnoreCase(name)) {
        return i;
      }
    }
    return 0;
  }

  /** The name of this source's quality: HIGH, MEDIUM or LOW; null when unset. */
  public @Nullable String qualityName() {
    return quality > 0 && quality < QUALITIES.length ? QUALITIES[quality] : null;
  }

  @Override
  public @NotNull EntityType type() {
    return EntityType.SOURCE;
  }

  @Override
  public boolean isEmpty() {
    return title.isEmpty() && publisher.isEmpty();
  }

  static @NotNull Source read(@NotNull ByteBuffer buf) {
    return new Source(
        Entity.getString(buf),
        Entity.getString(buf),
        buf.getInt(),
        buf.getInt(),
        buf.getShort(),
        buf.getShort());
  }

  @Override
  public byte @NotNull [] encode() {
    ByteBuffer buf =
        Entity.recordBuffer(Entity.stringSize(title) + Entity.stringSize(publisher) + 12);
    Entity.putString(buf, title);
    Entity.putString(buf, publisher);
    buf.putInt(publicationDate).putInt(date).putShort((short) version).putShort((short) quality);
    return Entity.finish(buf);
  }
}
