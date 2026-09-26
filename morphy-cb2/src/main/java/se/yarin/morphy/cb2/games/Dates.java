package se.yarin.morphy.cb2.games;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.Date;

/**
 * The packed date of the format: the day in bits 0-4, the month in bits 5-8 and the year in bits
 * 9-20, a part being 0 when unknown.
 */
public final class Dates {
  private Dates() {}

  public static @NotNull Date decode(int value) {
    value &= (1 << 21) - 1;
    return new Date(value >> 9, (value >> 5) & 15, value & 31);
  }

  public static int encode(@NotNull Date date) {
    return (date.year() << 9) | (date.month() << 5) | date.day();
  }
}
