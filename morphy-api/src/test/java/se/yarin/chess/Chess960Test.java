package se.yarin.chess;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Chess960Test {

  private String getFirstRank(Position p) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 8; i++) {
      sb.append(p.stoneAt(i, 0).toChar());
    }
    return sb.toString();
  }

  @Test
  public void testSpecificPosition() {
    assertEquals(Position.start(), Chess960.getStartPosition(Chess960.REGULAR_CHESS_SP));

    assertEquals("NRBBKRNQ", getFirstRank(Chess960.getStartPosition(373)));
    assertEquals("RKBQNBNR", getFirstRank(Chess960.getStartPosition(710)));
    assertEquals("NNRKQRBB", getFirstRank(Chess960.getStartPosition(79)));

    assertEquals(373, Chess960.getStartPositionNo("NRBBKRNQ"));
    assertEquals(710, Chess960.getStartPositionNo("RKBQNBNR"));
    assertEquals(79, Chess960.getStartPositionNo("nnrkqrbb"));
  }

  @Test
  public void testPiecePositions() {
    for (int i = 0; i < 960; i++) {
      Position p = Chess960.getStartPosition(i);
      int rook1Sqi = Chess960.getARookSqi(i, Player.BLACK);
      int rook2Sqi = Chess960.getHRookSqi(i, Player.BLACK);
      int kingSqi = Chess960.getKingSqi(i, Player.BLACK);
      assertTrue(rook1Sqi < kingSqi);
      assertTrue(kingSqi < rook2Sqi);
      assertEquals(Stone.BLACK_ROOK, p.stoneAt(rook1Sqi));
      assertEquals(Stone.BLACK_ROOK, p.stoneAt(rook2Sqi));
      assertEquals(Stone.BLACK_KING, p.stoneAt(kingSqi));
    }
  }
}
