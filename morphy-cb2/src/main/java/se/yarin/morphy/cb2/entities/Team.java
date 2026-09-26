package se.yarin.morphy.cb2.entities;

import java.nio.ByteBuffer;
import org.jetbrains.annotations.NotNull;

/**
 * A team.
 *
 * @param title the title
 * @param number the team number, 0 if not set
 * @param seasonFlags bit 0 set if the year denotes a season spanning two years
 * @param year the year, 0 if not set
 * @param nation the nation
 */
public record Team(@NotNull String title, int number, int seasonFlags, int year, int nation)
    implements Entity {

  public static @NotNull Team of(@NotNull String title) {
    return new Team(title, 0, 0, 0, 0);
  }

  public boolean season() {
    return (seasonFlags & 1) != 0;
  }

  @Override
  public @NotNull EntityType type() {
    return EntityType.TEAM;
  }

  @Override
  public boolean isEmpty() {
    return title.isEmpty();
  }

  static @NotNull Team read(@NotNull ByteBuffer buf) {
    return new Team(
        Entity.getString(buf),
        buf.get() & 0xFF,
        buf.get() & 0xFF,
        buf.getShort() & 0xFFFF,
        buf.get() & 0xFF);
  }

  @Override
  public byte @NotNull [] encode() {
    ByteBuffer buf = Entity.recordBuffer(Entity.stringSize(title) + 5);
    Entity.putString(buf, title);
    buf.put((byte) number).put((byte) seasonFlags).putShort((short) year).put((byte) nation);
    return Entity.finish(buf);
  }
}
