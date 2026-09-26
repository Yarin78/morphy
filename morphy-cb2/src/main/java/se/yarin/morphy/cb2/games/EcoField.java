package se.yarin.morphy.cb2.games;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.Chess960;
import se.yarin.chess.Eco;

/**
 * The field at 0x80 of a game record, which holds either an ECO code or a Chess960 start position.
 * {@code value / 128 − 1} is the ECO code numbered from 0 and {@code value % 128} the sub-ECO, 0
 * meaning no ECO; a value of 64576 or more is Chess960 start position {@code value − 64576}.
 */
public final class EcoField {
  private EcoField() {}

  public static final int CHESS960_BASE = 65536 - 960;

  /** Whether the value is a Chess960 start position rather than an ECO code. */
  public static boolean isChess960(int value) {
    return value >= CHESS960_BASE;
  }

  /** The Chess960 start position of a value that holds one. */
  public static int chess960(int value) {
    return value - CHESS960_BASE;
  }

  /** The ECO code of a value, unset for a Chess960 value or 0. */
  public static @NotNull Eco eco(int value) {
    int eco = value / 128 - 1;
    if (eco < 0 || isChess960(value) || eco >= 500 || value % 128 >= 100) {
      return Eco.unset();
    }
    return Eco.fromInt(eco, value % 128);
  }

  /** The value of an ECO code. */
  public static int of(@NotNull Eco eco) {
    return eco.isSet() ? (eco.getInt() + 1) * 128 + eco.getSubEco() : 0;
  }

  /**
   * The value of a Chess960 start position; the standard start position is stored as an ordinary
   * game, so it has none.
   */
  public static int ofChess960(int startPosition) {
    if (startPosition == Chess960.REGULAR_CHESS_SP) {
      throw new IllegalArgumentException("The standard start position has no Chess960 value");
    }
    return CHESS960_BASE + startPosition;
  }
}
