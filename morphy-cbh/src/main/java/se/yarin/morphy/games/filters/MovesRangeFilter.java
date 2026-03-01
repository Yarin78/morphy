package se.yarin.morphy.games.filters;

import java.nio.ByteBuffer;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.games.GameHeader;
import se.yarin.util.ByteBufferUtil;

public class MovesRangeFilter extends IsGameFilter {

  private final int minMoves;
  private final int maxMoves;

  public MovesRangeFilter(int minMoves, int maxMoves) {
    this.minMoves = minMoves;
    this.maxMoves = maxMoves;
  }

  public int minMoves() {
    return minMoves;
  }

  public int maxMoves() {
    return maxMoves;
  }

  @Override
  public boolean matches(int id, @NotNull GameHeader gameHeader) {
    if (!super.matches(id, gameHeader)) return false;
    int moves = gameHeader.noMoves();
    // -1 means more than 255 moves; treat as 256 for comparison purposes
    if (moves == -1) moves = 256;
    return moves >= minMoves && moves <= maxMoves;
  }

  @Override
  public boolean matchesSerialized(int id, @NotNull ByteBuffer buf) {
    if (!super.matchesSerialized(id, buf)) return false;
    // noMoves is stored as unsigned byte at offset 45
    // A value of 0 in the serialized form when it's a game means 0 moves (or unknown)
    // The -1 case (>255 moves) is stored as 0 in the byte; the deserialized form uses -1
    int moves = ByteBufferUtil.getUnsignedByte(buf, 45);
    // 0 is ambiguous: could be 0 moves or >255 moves
    // For >255 moves, we can't distinguish in serialized form, so match conservatively
    if (moves == 0 && maxMoves >= 256) return true;
    return moves >= minMoves && moves <= maxMoves;
  }

  @Override
  public String toString() {
    if (minMoves == 0 && maxMoves == Integer.MAX_VALUE) {
      return "true";
    } else if (minMoves == maxMoves) {
      return "moves = " + minMoves;
    } else if (minMoves == 0) {
      return "moves <= " + maxMoves;
    } else if (maxMoves == Integer.MAX_VALUE) {
      return "moves >= " + minMoves;
    } else {
      return "moves >= " + minMoves + " and moves <= " + maxMoves;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MovesRangeFilter that = (MovesRangeFilter) o;
    return minMoves == that.minMoves && maxMoves == that.maxMoves;
  }

  @Override
  public int hashCode() {
    return Objects.hash(minMoves, maxMoves);
  }
}
