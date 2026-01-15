package se.yarin.morphy.games.filters;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.GameResult;
import se.yarin.morphy.games.GameHeader;
import se.yarin.morphy.util.CBUtil;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class ResultsFilter extends IsGameFilter {
  private @NotNull GameResult result;

  public ResultsFilter(@NotNull String results) {
    switch (results.toLowerCase()) {
      case "1-0", "white" -> result = GameResult.WHITE_WINS;
      case "i-o" -> result = GameResult.WHITE_WINS_ON_FORFEIT;
      case "0-1", "black" -> result = GameResult.BLACK_WINS;
      case "o-i" -> result = GameResult.BLACK_WINS_ON_FORFEIT;
      case "draw" -> result = GameResult.DRAW;
      case "0-0", "o-o" -> result = GameResult.BOTH_LOST;
      default -> throw new IllegalArgumentException("Invalid result: " + results);
    }
  }

  @Override
  public boolean matches(int id, @NotNull GameHeader header) {
    return super.matches(id, header) && result.equals(header.result());
  }

  @Override
  public boolean matchesSerialized(int id, @NotNull ByteBuffer buf) {
    return super.matchesSerialized(id, buf)
        && result.equals(CBUtil.decodeGameResult(ByteBufferUtil.getUnsignedByte(buf, 27)));
  }

  @Override
  public String toString() {
    return "result = '" + result + "'";
  }
}
