package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.games.GameHeader;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RatingRangeFilter extends IsGameFilter {
  private static final Pattern ratingRangePattern = Pattern.compile("^([0-9]+)?-([0-9]+)?$");

  private final int minRating;
  private final int maxRating;

  @NotNull private final RatingColor color;

  public int minRating() {
    return minRating;
  }

  public int maxRating() {
    return maxRating;
  }

  public enum RatingColor {
    ANY,
    BOTH,
    WHITE,
    BLACK,
    AVERAGE,
    DIFFERENCE,
  }

  public RatingRangeFilter(int minRating, int maxRating, @NotNull RatingColor color) {
    this.minRating = minRating;
    this.maxRating = maxRating;
    this.color = color;
  }

  public RatingRangeFilter(@NotNull String ratingRange, @NotNull RatingColor color) {
    this.color = color;
    Matcher matcher = ratingRangePattern.matcher(ratingRange);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid rating range specified: " + ratingRange);
    }
    this.minRating = matcher.group(1) == null ? 0 : Integer.parseInt(matcher.group(1));
    this.maxRating = matcher.group(2) == null ? 9999 : Integer.parseInt(matcher.group(2));
  }

  @Override
  public boolean matches(int id, @NotNull GameHeader game) {
    return super.matches(id, game) && matches(game.whiteElo(), game.blackElo());
  }

  private boolean matches(int whiteRating, int blackRating) {
    boolean whiteMatches = whiteRating >= minRating && whiteRating <= maxRating;
    boolean blackMatches = blackRating >= minRating && blackRating <= maxRating;
    switch (color) {
      case ANY -> {
        return whiteMatches || blackMatches;
      }
      case BOTH -> {
        return whiteMatches && blackMatches;
      }
      case WHITE -> {
        return whiteMatches;
      }
      case BLACK -> {
        return blackMatches;
      }
      case AVERAGE -> {
        return (whiteRating + blackRating) / 2 >= minRating
            && (whiteRating + blackRating) / 2 <= maxRating;
      }
      case DIFFERENCE -> {
        return whiteRating > 0
            && blackRating > 0
            && Math.abs(whiteRating - blackRating) >= minRating
            && Math.abs(whiteRating - blackRating) <= maxRating;
      }
      default -> throw new IllegalStateException("Unexpected value: " + color);
    }
  }

  @Override
  public boolean matchesSerialized(int id, @NotNull ByteBuffer buf) {
    if (!super.matchesSerialized(id, buf)) {
      return false;
    }

    int whiteRating = ByteBufferUtil.getUnsignedShortB(buf, 31);
    int blackRating = ByteBufferUtil.getUnsignedShortB(buf, 33);
    return matches(whiteRating, blackRating);
  }

  @Override
  public String toString() {
    // TODO: color
    if (minRating == 0) {
      return "rating <= " + maxRating;
    } else if (maxRating == 9999) {
      return "rating >= " + minRating;
    } else {
      return "rating >= " + minRating + " and rating <= " + maxRating;
    }
  }
}
