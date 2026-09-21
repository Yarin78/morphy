package se.yarin.morphy.util;

import org.junit.Test;
import se.yarin.chess.GameResult;

import static org.junit.Assert.assertEquals;

public class CBUtilTest {

  @Test
  public void testGameResultValues() {
    // The values stored in the database. In particular, 4 is a win for black on forfeit and 6 is a
    // win for white on forfeit.
    assertEquals(GameResult.BLACK_WINS, CBUtil.decodeGameResult(0));
    assertEquals(GameResult.DRAW, CBUtil.decodeGameResult(1));
    assertEquals(GameResult.WHITE_WINS, CBUtil.decodeGameResult(2));
    assertEquals(GameResult.NOT_FINISHED, CBUtil.decodeGameResult(3));
    assertEquals(GameResult.BLACK_WINS_ON_FORFEIT, CBUtil.decodeGameResult(4));
    assertEquals(GameResult.DRAW_ON_FORFEIT, CBUtil.decodeGameResult(5));
    assertEquals(GameResult.WHITE_WINS_ON_FORFEIT, CBUtil.decodeGameResult(6));
    assertEquals(GameResult.BOTH_LOST, CBUtil.decodeGameResult(7));
  }

  @Test
  public void testGameResultRoundTrip() {
    for (GameResult result : GameResult.values()) {
      assertEquals(result, CBUtil.decodeGameResult(CBUtil.encodeGameResult(result)));
    }
    assertEquals(4, CBUtil.encodeGameResult(GameResult.BLACK_WINS_ON_FORFEIT));
    assertEquals(6, CBUtil.encodeGameResult(GameResult.WHITE_WINS_ON_FORFEIT));
  }
}
