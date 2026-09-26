package se.yarin.morphy.cb2.entities;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * A tournament. See format/v2/4-entities.md#tournament.
 *
 * @param place the place
 * @param title the title
 * @param startDate the start date, packed as in {@link se.yarin.morphy.cb2.games.Dates}
 * @param typeAndTimeControl the type in the low 5 bits (1 single game, 2 match, 3 round robin, 4
 *     swiss, 5 team, 6 knockout, 7 simultaneous, 8 scheveningen), bit 5 blitz, bit 6 rapid, bit 7
 *     correspondence
 * @param teamFlags bit 0 set for a team tournament
 * @param nation the nation at the time of the tournament
 * @param unknown7 the byte at 7, always 0
 * @param category the category
 * @param flags bits 0 and 1 complete, bit 2 board points, bit 3 three points for a win
 * @param rounds the number of rounds
 * @param unknown11 the byte at 11, always 0
 * @param latitude the latitude of the place, 0 if unknown
 * @param longitude the longitude of the place
 * @param placeNation the nation the place lies in today, 0 if unknown
 * @param unknown21 the 37 bytes at 21, of unknown meaning
 * @param tiebreaks the tiebreak rules, in the order they apply
 * @param endDate the end date
 * @param trailing the 44 bytes after the end date, always 0
 */
public record Tournament(
    @NotNull String place,
    @NotNull String title,
    int startDate,
    int typeAndTimeControl,
    int teamFlags,
    int nation,
    int unknown7,
    int category,
    int flags,
    int rounds,
    int unknown11,
    float latitude,
    float longitude,
    int placeNation,
    byte @NotNull [] unknown21,
    @NotNull List<Integer> tiebreaks,
    int endDate,
    byte @NotNull [] trailing)
    implements Entity {

  private static final int UNKNOWN_21_SIZE = 37;
  private static final int TRAILING_SIZE = 44;

  /** The 37 bytes at 21 that most tournaments have: a 7 at 34, 45 and 56. */
  public static byte @NotNull [] standardUnknown21() {
    byte[] bytes = new byte[UNKNOWN_21_SIZE];
    bytes[34 - 21] = 7;
    bytes[45 - 21] = 7;
    bytes[56 - 21] = 7;
    return bytes;
  }

  /** A tournament with a title, place and start date, and nothing else set. */
  public static @NotNull Tournament of(@NotNull String title, @NotNull String place, int startDate) {
    return new Tournament(
        place, title, startDate, 0, 0, 0, 0, 0, 0, 0, 0, 0f, 0f, 0, standardUnknown21(), List.of(), 0,
        new byte[TRAILING_SIZE]);
  }

  public Tournament {
    tiebreaks = List.copyOf(tiebreaks);
  }

  @Override
  public @NotNull EntityType type() {
    return EntityType.TOURNAMENT;
  }

  @Override
  public boolean isEmpty() {
    return title.isEmpty() && place.isEmpty() && startDate == 0;
  }

  /** The type of tournament, the low 5 bits of {@link #typeAndTimeControl()}. */
  public int tournamentType() {
    return typeAndTimeControl & 31;
  }

  public boolean blitz() {
    return (typeAndTimeControl & 0x20) != 0;
  }

  public boolean rapid() {
    return (typeAndTimeControl & 0x40) != 0;
  }

  public boolean correspondence() {
    return (typeAndTimeControl & 0x80) != 0;
  }

  public boolean teamTournament() {
    return (teamFlags & 1) != 0;
  }

  public boolean complete() {
    return (flags & 3) != 0;
  }

  public boolean boardPoints() {
    return (flags & 4) != 0;
  }

  public boolean threePointsForWin() {
    return (flags & 8) != 0;
  }

  static @NotNull Tournament read(@NotNull ByteBuffer buf) {
    String place = Entity.getString(buf);
    String title = Entity.getString(buf);
    int startDate = buf.getInt();
    int type = buf.get() & 0xFF, team = buf.get() & 0xFF, nation = buf.get() & 0xFF;
    int unknown7 = buf.get() & 0xFF, category = buf.get() & 0xFF, flags = buf.get() & 0xFF;
    int rounds = buf.get() & 0xFF, unknown11 = buf.get() & 0xFF;
    float latitude = buf.getFloat(), longitude = buf.getFloat();
    int placeNation = buf.get() & 0xFF;
    byte[] unknown21 = new byte[UNKNOWN_21_SIZE];
    buf.get(unknown21);
    int count = buf.getShort();
    List<Integer> tiebreaks = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      tiebreaks.add((int) buf.getShort());
    }
    int endDate = buf.getInt();
    byte[] trailing = new byte[TRAILING_SIZE];
    buf.get(trailing);
    return new Tournament(
        place, title, startDate, type, team, nation, unknown7, category, flags, rounds, unknown11,
        latitude, longitude, placeNation, unknown21, tiebreaks, endDate, trailing);
  }

  @Override
  public byte @NotNull [] encode() {
    ByteBuffer buf =
        Entity.recordBuffer(
            Entity.stringSize(place) + Entity.stringSize(title) + 108 + 2 * tiebreaks.size());
    Entity.putString(buf, place);
    Entity.putString(buf, title);
    buf.putInt(startDate);
    buf.put((byte) typeAndTimeControl).put((byte) teamFlags).put((byte) nation);
    buf.put((byte) unknown7).put((byte) category).put((byte) flags);
    buf.put((byte) rounds).put((byte) unknown11);
    buf.putFloat(latitude).putFloat(longitude);
    buf.put((byte) placeNation);
    buf.put(unknown21);
    buf.putShort((short) tiebreaks.size());
    for (int rule : tiebreaks) {
      buf.putShort((short) rule);
    }
    buf.putInt(endDate);
    buf.put(trailing);
    return Entity.finish(buf);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Tournament t
        && place.equals(t.place)
        && title.equals(t.title)
        && startDate == t.startDate
        && typeAndTimeControl == t.typeAndTimeControl
        && teamFlags == t.teamFlags
        && nation == t.nation
        && unknown7 == t.unknown7
        && category == t.category
        && flags == t.flags
        && rounds == t.rounds
        && unknown11 == t.unknown11
        && Float.compare(latitude, t.latitude) == 0
        && Float.compare(longitude, t.longitude) == 0
        && placeNation == t.placeNation
        && Arrays.equals(unknown21, t.unknown21)
        && tiebreaks.equals(t.tiebreaks)
        && endDate == t.endDate
        && Arrays.equals(trailing, t.trailing);
  }

  @Override
  public int hashCode() {
    return 31 * (31 * place.hashCode() + title.hashCode()) + startDate;
  }
}
