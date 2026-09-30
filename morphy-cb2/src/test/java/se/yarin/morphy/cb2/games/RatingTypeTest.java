package se.yarin.morphy.cb2.games;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import se.yarin.chess.EloType;
import se.yarin.chess.EloType.TimeControl;

class RatingTypeTest {

  private static RatingType read(String hex) {
    return RatingType.read(ByteBuffer.wrap(HexFormat.of().parseHex(hex)).order(ByteOrder.LITTLE_ENDIAN));
  }

  private static String write(RatingType type) {
    ByteBuffer buf = ByteBuffer.allocate(RatingType.SIZE).order(ByteOrder.LITTLE_ENDIAN);
    type.write(buf);
    return HexFormat.of().formatHex(buf.array());
  }

  /** Each rating type ChessBase offers, as ChessBase stored it, and what it is. */
  private static final Object[][] CHESSBASE_RECORDS = {
    {"0100010000004649444500000000", EloType.international(TimeControl.NORMAL)},
    {"1100020000004649444500000000", EloType.international(TimeControl.BLITZ)},
    {"2100040000004943434600000000", EloType.international(TimeControl.CORRESPONDENCE)},
    {"03000500c4004342000000000000", EloType.server(TimeControl.NORMAL, EloType.CHESSBASE)},
    {"0b000600c4004342000000000000", EloType.server(TimeControl.BULLET, EloType.CHESSBASE)},
    {"13000700c4004342000000000000", EloType.server(TimeControl.BLITZ, EloType.CHESSBASE)},
    // ChessBase's own rapid is stored with time control 5
    {"2b000800c4004342000000000000", EloType.server(TimeControl.RAPID, EloType.CHESSBASE)},
    {"13000c00c40063686573732e636f", EloType.server(TimeControl.BLITZ, EloType.CHESS_COM)},
    {"1b000d00c40063686573732e636f", EloType.server(TimeControl.RAPID, EloType.CHESS_COM)},
    {"03000e00c4004c69436865737300", EloType.server(TimeControl.NORMAL, EloType.LICHESS)},
    {"0b000f00c4004c69436865737300", EloType.server(TimeControl.BULLET, EloType.LICHESS)},
    {"13001000c4004c69436865737300", EloType.server(TimeControl.BLITZ, EloType.LICHESS)},
    {"1b001100c4004c69436865737300", EloType.server(TimeControl.RAPID, EloType.LICHESS)},
  };

  @Test
  void readsAndWritesAsChessBase() {
    for (Object[] record : CHESSBASE_RECORDS) {
      String hex = (String) record[0];
      EloType type = (EloType) record[1];
      assertEquals(type, read(hex).toEloType(), hex);
      assertEquals(hex, write(RatingType.of(type)), type.toString());
    }
  }

  @Test
  void chessComWithoutItsNation() {
    // ChessBase sometimes stores a chess.com rating without the internet as its nation; it's the
    // same rating, and is written with it
    EloType normal = EloType.server(TimeControl.NORMAL, EloType.CHESS_COM);
    assertEquals(normal, read("03000a00000063686573732e636f").toEloType());
    assertEquals("03000a00c40063686573732e636f", write(RatingType.of(normal)));
    assertEquals(
        EloType.server(TimeControl.BULLET, EloType.CHESS_COM), read("0b000b00000063686573732e636f").toEloType());
  }

  @Test
  void typesChessBaseDoesNotOffer() {
    assertNull(RatingType.of(EloType.international(TimeControl.BULLET)));
    assertNull(RatingType.of(EloType.server(TimeControl.CORRESPONDENCE, EloType.LICHESS)));
    assertNull(RatingType.of(EloType.server(TimeControl.BLITZ, "Playchess")));
  }

  @Test
  void nationalRating() {
    EloType type = EloType.national(TimeControl.RAPID, "NOR");
    RatingType stored = RatingType.of(type);
    assertEquals(100, stored.list());
    assertEquals(type, stored.toEloType());
  }
}
