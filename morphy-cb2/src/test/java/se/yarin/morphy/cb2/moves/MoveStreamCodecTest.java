package se.yarin.morphy.cb2.moves;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import se.yarin.chess.Castles;
import se.yarin.chess.Chess;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Player;
import se.yarin.chess.Position;
import se.yarin.morphy.cb2.storage.RecordFile;

class MoveStreamCodecTest {

  // The byte of a set-up position's en passant file: the high byte of its third word
  private static final int EN_PASSANT_BYTE = 5;

  @Test
  void enPassantByteOfFifteenIsNone() {
    Position start =
        Position.fromString(
            "....k...\n" + "........\n" + "........\n" + "........\n" + "........\n" + "........\n"
                + "........\n" + "....K...\n",
            Player.WHITE,
            EnumSet.noneOf(Castles.class),
            Chess.NO_COL);
    GameMovesModel model = new GameMovesModel(start, 40);
    byte[] content = MoveStreamCodec.encode(model).content();
    assertEquals(0, content[EN_PASSANT_BYTE]);

    content[EN_PASSANT_BYTE] = 15;
    Position decoded = MoveStreamCodec.decode(RecordFile.TAG_GAME, content).root().position();

    assertEquals(Chess.NO_COL, decoded.getEnPassantCol());
    assertEquals(start, decoded);
  }
}
