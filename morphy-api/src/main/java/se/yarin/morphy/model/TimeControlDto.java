package se.yarin.morphy.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The time control of a game: one or more periods, each played after the one before, like 90
 * minutes for 40 moves and then 30 minutes with a 30 second increment for the rest of the game.
 *
 * @param periods the periods, in the order they're played; the last is for the rest of the game
 *     unless it has a number of moves
 */
public record TimeControlDto(@NotNull List<Period> periods) {

  public TimeControlDto {
    periods = List.copyOf(periods);
  }

  private static final Pattern PGN_PERIOD = Pattern.compile("(?:(\\d+)/)?(\\d+)(?:\\+(\\d+))?");

  /**
   * The time control as a PGN TimeControl tag: the periods separated by ":", each the time in
   * seconds, after the number of moves and a "/" if it has one, and followed by "+" and the
   * increment if it has one, like {@code 40/5400+30:1800+30}.
   */
  public @NotNull String toPgn() {
    StringBuilder sb = new StringBuilder();
    for (Period period : periods) {
      if (!sb.isEmpty()) {
        sb.append(':');
      }
      if (period.moves() != null) {
        sb.append(period.moves()).append('/');
      }
      sb.append(period.seconds());
      if (period.increment() > 0) {
        sb.append('+').append(period.increment());
      }
    }
    return sb.toString();
  }

  /**
   * Reads a PGN TimeControl tag, see {@link #toPgn()}; null for an unknown ("?") or no ("-") time
   * control, and for one that can't be read, like a sandclock.
   */
  public static @Nullable TimeControlDto fromPgn(@Nullable String tag) {
    if (tag == null || tag.isBlank()) {
      return null;
    }
    List<Period> periods = new ArrayList<>();
    for (String field : tag.strip().split(":")) {
      Matcher m = PGN_PERIOD.matcher(field.strip());
      if (!m.matches()) {
        return null;
      }
      try {
        periods.add(
            new Period(
                Integer.parseInt(m.group(2)),
                m.group(3) == null ? 0 : Integer.parseInt(m.group(3)),
                m.group(1) == null ? null : Integer.parseInt(m.group(1))));
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return new TimeControlDto(periods);
  }

  /**
   * One period of a time control.
   *
   * @param seconds the time the period starts with, in seconds
   * @param increment the time added after each move, in seconds
   * @param moves the number of moves of the period; null for the rest of the game
   */
  public record Period(int seconds, int increment, @Nullable Integer moves) {}
}
