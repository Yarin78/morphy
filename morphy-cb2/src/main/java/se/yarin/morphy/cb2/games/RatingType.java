package se.yarin.morphy.cb2.games;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.EloType;
import se.yarin.morphy.chessbase.Nation;

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

  /** The server names as stored, which chess.com's is truncated to fit, and as {@link EloType} has them. */
  private static final Map<String, String> SERVER_NAMES =
      Map.of("CB", EloType.CHESSBASE, "chess.co", EloType.CHESS_COM, "LiChess", EloType.LICHESS);

  private static final int INTERNATIONAL = 1;
  private static final int NATIONAL = 2;
  private static final int SERVER = 3;
  private static final int NATIONAL_LIST = 100;

  public int kind() {
    return kindAndTimeControl & 7;
  }

  public int timeControl() {
    return kindAndTimeControl >> 3;
  }

  /**
   * ChessBase stores a rapid rating on its own server with this time control rather than the usual
   * one for rapid.
   */
  private static final int CHESSBASE_RAPID = 5;

  /**
   * The rating lists of the servers, at normal, bullet, blitz and rapid; the only time controls a
   * server rating can have.
   */
  private static final Map<String, int[]> SERVER_LISTS =
      Map.of(
          EloType.CHESSBASE, new int[] {5, 6, 7, 8},
          EloType.CHESS_COM, new int[] {10, 11, 12, 13},
          EloType.LICHESS, new int[] {14, 15, 16, 17});

  /** This rating type in the neutral form; null when it has no kind. */
  public @Nullable EloType toEloType() {
    EloType.TimeControl[] timeControls = EloType.TimeControl.values();
    EloType.TimeControl timeControl;
    if (timeControl() == CHESSBASE_RAPID) {
      timeControl = EloType.TimeControl.RAPID;
    } else if (timeControl() < timeControls.length) {
      timeControl = timeControls[timeControl()];
    } else {
      return null;
    }
    return switch (kind()) {
      case INTERNATIONAL -> new EloType(EloType.Kind.INTERNATIONAL, timeControl, null, name.isEmpty() ? null : name);
      case NATIONAL ->
          nation > 0 && nation < Nation.values().length
              ? EloType.national(timeControl, Nation.values()[nation].getIocCode())
              : null;
      case SERVER -> EloType.server(timeControl, SERVER_NAMES.getOrDefault(name, name));
      default -> null;
    };
  }

  /**
   * An elo type as stored; null for one that can't be: an international rating at bullet, a server
   * rating at correspondence or on an unknown server, or a national rating without a known nation.
   * International ratings are FIDE at normal, blitz and rapid (lists 1 to 3) and ICCF at
   * correspondence (4); a national rating is list 100 whatever its nation and time control; each
   * server has a list per time control, see {@link #SERVER_LISTS}.
   */
  public static @Nullable RatingType of(@NotNull EloType type) {
    int timeControl = type.timeControl().ordinal();
    return switch (type.kind()) {
      case INTERNATIONAL -> {
        int list =
            switch (type.timeControl()) {
              case NORMAL -> 1;
              case BLITZ -> 2;
              case RAPID -> 3;
              case CORRESPONDENCE -> 4;
              case BULLET -> 0;
            };
        String name = type.timeControl() == EloType.TimeControl.CORRESPONDENCE ? "ICCF" : "FIDE";
        yield list == 0 ? null : new RatingType(INTERNATIONAL | timeControl << 3, list, 0, name);
      }
      case NATIONAL -> {
        Nation nation = type.nation() == null ? Nation.NONE : Nation.fromIOC(type.nation());
        yield nation == Nation.NONE
            ? null
            : new RatingType(NATIONAL | timeControl << 3, NATIONAL_LIST, nation.ordinal(), "");
      }
      case SERVER -> {
        int[] lists = type.name() == null ? null : SERVER_LISTS.get(type.name());
        if (lists == null || timeControl >= lists.length) {
          yield null;
        }
        if (type.name().equals(EloType.CHESSBASE) && type.timeControl() == EloType.TimeControl.RAPID) {
          timeControl = CHESSBASE_RAPID;
        }
        yield new RatingType(
            SERVER | timeControl << 3, lists[type.timeControl().ordinal()], Nation.INTERNET.ordinal(), storedServerName(type.name()));
      }
    };
  }

  /** A server's name as stored, the reverse of {@link #SERVER_NAMES}. */
  private static String storedServerName(String name) {
    return SERVER_NAMES.entrySet().stream()
        .filter(e -> e.getValue().equals(name))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(name);
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
