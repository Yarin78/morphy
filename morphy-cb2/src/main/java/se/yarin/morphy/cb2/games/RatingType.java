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

  /** This rating type in the neutral form; null when it has no kind. */
  public @Nullable EloType toEloType() {
    EloType.TimeControl[] timeControls = EloType.TimeControl.values();
    if (timeControl() >= timeControls.length) {
      return null;
    }
    EloType.TimeControl timeControl = timeControls[timeControl()];
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
   * An elo type as stored; null for a national rating without a known nation. Each international
   * and server rating list is one provider at one time control, and not all their ids are known: an
   * unknown one is stored as 0, and a chess.com rating always as 10, the only chess.com list known.
   * A national rating is list 100 whatever its nation and time control.
   */
  public static @Nullable RatingType of(@NotNull EloType type) {
    int kindAndTimeControl = type.timeControl().ordinal() << 3;
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
        yield new RatingType(INTERNATIONAL | kindAndTimeControl, list, 0, name);
      }
      case NATIONAL -> {
        Nation nation = type.nation() == null ? Nation.NONE : Nation.fromIOC(type.nation());
        yield nation == Nation.NONE
            ? null
            : new RatingType(NATIONAL | kindAndTimeControl, NATIONAL_LIST, nation.ordinal(), "");
      }
      case SERVER -> {
        String name = type.name() == null ? "" : type.name();
        int list =
            switch (name) {
              case EloType.CHESSBASE -> type.timeControl() == EloType.TimeControl.BULLET ? 6 : 0;
              case EloType.CHESS_COM -> 10;
              case EloType.LICHESS ->
                  switch (type.timeControl()) {
                    case BLITZ -> 16;
                    case RAPID -> 17;
                    default -> 0;
                  };
              default -> 0;
            };
        // chess.com is the one server stored without the internet as its nation
        int nation = name.equals(EloType.CHESS_COM) ? 0 : Nation.INTERNET.ordinal();
        yield new RatingType(SERVER | kindAndTimeControl, list, nation, storedServerName(name));
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
