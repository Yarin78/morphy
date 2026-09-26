package se.yarin.morphy.cb2.entities;

import java.nio.ByteBuffer;
import org.jetbrains.annotations.NotNull;

/**
 * A player, who may also be an annotator or the author of a text or analysis.
 *
 * @param lastName the last name
 * @param firstName the first name, empty if none
 * @param unknown1 always 0; likely the length of a string that is always empty
 * @param unknown2 always 0; likely the length of a string that is always empty
 * @param chessBaseId the id in ChessBase's player database: -1 not looked up, 0 no match
 * @param fideIdSize always 8, likely the size of the FIDE id
 * @param fideId the FIDE id: -1 not looked up, 0 none
 */
public record Player(
    @NotNull String lastName,
    @NotNull String firstName,
    int unknown1,
    int unknown2,
    int chessBaseId,
    int fideIdSize,
    long fideId)
    implements Entity {

  /** A player never looked up in ChessBase's player database. */
  public static @NotNull Player of(@NotNull String lastName, @NotNull String firstName) {
    return new Player(lastName, firstName, 0, 0, -1, 8, -1);
  }

  /** A player from a full name, {@code "Last, First"} or just a last name. */
  public static @NotNull Player ofFullName(@NotNull String fullName) {
    int comma = fullName.indexOf(',');
    if (comma < 0) {
      return of(fullName.strip(), "");
    }
    return of(fullName.substring(0, comma).strip(), fullName.substring(comma + 1).strip());
  }

  /** The name as {@code "Last, First"}, or just the last name. */
  public @NotNull String fullName() {
    return firstName.isEmpty() ? lastName : lastName + ", " + firstName;
  }

  @Override
  public @NotNull EntityType type() {
    return EntityType.PLAYER;
  }

  @Override
  public boolean isEmpty() {
    return lastName.isEmpty() && firstName.isEmpty();
  }

  /** The same player with other names, keeping the rest. */
  public @NotNull Player withNames(@NotNull String lastName, @NotNull String firstName) {
    return new Player(lastName, firstName, unknown1, unknown2, chessBaseId, fideIdSize, fideId);
  }

  static @NotNull Player read(@NotNull ByteBuffer buf) {
    return new Player(
        Entity.getString(buf),
        Entity.getString(buf),
        buf.getInt(),
        buf.getInt(),
        buf.getInt(),
        buf.getInt(),
        buf.getLong());
  }

  @Override
  public byte @NotNull [] encode() {
    ByteBuffer buf =
        Entity.recordBuffer(Entity.stringSize(lastName) + Entity.stringSize(firstName) + 24);
    Entity.putString(buf, lastName);
    Entity.putString(buf, firstName);
    buf.putInt(unknown1).putInt(unknown2).putInt(chessBaseId).putInt(fideIdSize).putLong(fideId);
    return Entity.finish(buf);
  }
}
