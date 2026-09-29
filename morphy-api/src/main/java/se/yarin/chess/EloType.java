package se.yarin.chess;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What kind of rating an elo is: an international one (FIDE, or ICCF for correspondence), a
 * national one, or one from a chess server, at some time control. Each database format stores it
 * in its own way, and not every format can store every kind.
 *
 * @param kind international, national or server
 * @param timeControl the time control the rating is for
 * @param nation the IOC code of the nation of a national rating, e.g. {@code NOR}; null otherwise
 * @param name the name of an international rating ({@code FIDE} or {@code ICCF}) or of the server
 *     ({@link #CHESSBASE}, {@link #CHESS_COM}, {@link #LICHESS}, or a name as stored); null for a
 *     national rating
 */
public record EloType(
    @NotNull Kind kind, @NotNull TimeControl timeControl, @Nullable String nation, @Nullable String name) {

  public enum Kind {
    INTERNATIONAL,
    NATIONAL,
    SERVER
  }

  public enum TimeControl {
    NORMAL,
    BULLET,
    BLITZ,
    RAPID,
    CORRESPONDENCE
  }

  public static final String CHESSBASE = "ChessBase";
  public static final String CHESS_COM = "chess.com";
  public static final String LICHESS = "lichess";

  /** A FIDE rating at a normal time control, the usual kind. */
  public static final EloType FIDE = international(TimeControl.NORMAL);

  /** An international rating: ICCF for correspondence, otherwise FIDE. */
  public static @NotNull EloType international(@NotNull TimeControl timeControl) {
    return new EloType(
        Kind.INTERNATIONAL, timeControl, null, timeControl == TimeControl.CORRESPONDENCE ? "ICCF" : "FIDE");
  }

  public static @NotNull EloType national(@NotNull TimeControl timeControl, @NotNull String nation) {
    return new EloType(Kind.NATIONAL, timeControl, nation, null);
  }

  public static @NotNull EloType server(@NotNull TimeControl timeControl, @NotNull String name) {
    return new EloType(Kind.SERVER, timeControl, null, name);
  }

  /** A compact text form, "kind/timeControl/nation/name", e.g. {@code NATIONAL/BLITZ/NOR/}. */
  public @NotNull String encode() {
    return kind + "/" + timeControl + "/" + (nation == null ? "" : nation) + "/" + (name == null ? "" : name);
  }

  /** Reads the text form of {@link #encode()}; null if it isn't one. */
  public static @Nullable EloType decode(@NotNull String text) {
    String[] parts = text.split("/", 4);
    if (parts.length != 4) {
      return null;
    }
    try {
      return new EloType(
          Kind.valueOf(parts[0]),
          TimeControl.valueOf(parts[1]),
          parts[2].isEmpty() ? null : parts[2],
          parts[3].isEmpty() ? null : parts[3]);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
