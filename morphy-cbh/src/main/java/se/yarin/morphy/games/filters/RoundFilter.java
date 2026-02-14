package se.yarin.morphy.games.filters;

import java.nio.ByteBuffer;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.storage.ItemStorageFilter;
import se.yarin.util.ByteBufferUtil;

/**
 * Filters games by round and optional sub-round.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Round 5 (any sub-round): {@code new RoundFilter(5)}
 *   <li>Round 5, sub-round 2: {@code new RoundFilter(5, 2)}
 * </ul>
 */
public class RoundFilter implements ItemStorageFilter<GameHeader>, GameFilter {
  private final int round;
  private final int subRound;
  private final boolean matchSubRound;

  /**
   * Creates a round filter that matches any game in the specified round (regardless of sub-round).
   *
   * @param round the round number to match (1-255, or 0 for unset)
   */
  public RoundFilter(int round) {
    this(round, 0, false);
  }

  /**
   * Creates a round filter that matches games in the specified round and sub-round.
   *
   * @param round the round number to match (1-255, or 0 for unset)
   * @param subRound the sub-round number to match (1-255, or 0 for unset)
   */
  public RoundFilter(int round, int subRound) {
    this(round, subRound, true);
  }

  private RoundFilter(int round, int subRound, boolean matchSubRound) {
    if (round < 0 || round > 255) {
      throw new IllegalArgumentException("Round must be between 0 and 255, got: " + round);
    }
    if (subRound < 0 || subRound > 255) {
      throw new IllegalArgumentException("Sub-round must be between 0 and 255, got: " + subRound);
    }
    this.round = round;
    this.subRound = subRound;
    this.matchSubRound = matchSubRound;
  }

  public int round() {
    return round;
  }

  public int subRound() {
    return subRound;
  }

  public boolean matchesSubRound() {
    return matchSubRound;
  }

  @Override
  public boolean matches(int id, @NotNull GameHeader header) {
    if (header.round() != this.round) {
      return false;
    }

    if (matchSubRound && header.subRound() != this.subRound) {
      return false;
    }

    return true;
  }

  @Override
  public String toString() {
    if (matchSubRound) {
      return "round = " + round + ", subRound = " + subRound;
    } else {
      return "round = " + round;
    }
  }

  @Override
  public boolean matchesSerialized(int id, @NotNull ByteBuffer buf) {
    int round, subRound;

    if (IsGameFilter.isGame(buf)) {
      // Regular game
      round = ByteBufferUtil.getUnsignedByte(buf, 29);
      subRound = ByteBufferUtil.getUnsignedByte(buf, 30);
    } else {
      // Guiding text
      round = ByteBufferUtil.getUnsignedByte(buf, 16);
      subRound = ByteBufferUtil.getUnsignedByte(buf, 17);
    }

    if (round != this.round) {
      return false;
    }

    if (matchSubRound && subRound != this.subRound) {
      return false;
    }

    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RoundFilter that = (RoundFilter) o;
    return round == that.round && subRound == that.subRound && matchSubRound == that.matchSubRound;
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(round, subRound, matchSubRound);
  }
}
