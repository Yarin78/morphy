package se.yarin.morphy.cb2.moves;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.yarin.chess.Chess;
import se.yarin.chess.Chess960;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Player;
import se.yarin.chess.Position;
import se.yarin.chess.Stone;
import se.yarin.chess.pgn.PgnParser;
import se.yarin.morphy.cb2.storage.RecordFile;

/** The move words and the move tree, against the examples of format/v2/2-moves.md. */
class MoveWordsTest {

  @Test
  void kingWordsMatchTheSpecification() {
    // The white king on a1 reaches a2 first and b1 second, six words each
    assertEquals(Chess.strToSqi("a1"), MoveWords.word(1).from());
    assertEquals(Chess.strToSqi("a2"), MoveWords.word(1).to());
    assertEquals(Chess.strToSqi("b1"), MoveWords.word(7).to());
    // The white queen block begins after 6 · 420 king words
    assertEquals(Stone.WHITE_KING, MoveWords.word(0x09d8).stone());
    assertEquals(Stone.WHITE_QUEEN, MoveWords.word(0x09d9).stone());
    assertEquals(Stone.WHITE_ROOK, MoveWords.word(0x55f8).stone());
    assertEquals(Stone.BLACK_KING, MoveWords.word(0x55f9).stone());
    assertEquals(Stone.WHITE_PAWN, MoveWords.word(0xabf1).stone());
    assertEquals(Stone.BLACK_PAWN, MoveWords.word(0xae8d).stone());
    assertEquals(2, MoveWords.word(0xb12a).castles());
    assertEquals(1, MoveWords.word(0xb12b).castles());
  }

  @Test
  void castlingWords() throws Exception {
    GameMovesModel model = new PgnParser().parseMoves("1. e4 e5 2. Nf3 Nf6 3. Bc4 Bc5 4. O-O O-O");
    List<Integer> words = words(MoveStreamCodec.encode(model).content());
    assertEquals(0xb12a, words.get(words.size() - 3));
    assertEquals(0xb12c, words.get(words.size() - 2));
  }

  @Test
  void chess960CastlingUsesTheStartPositionsWords() {
    Position start = Chess960.getStartPosition(0);
    assertEquals(0, start.chess960StartPosition());
    GameMovesModel model = new GameMovesModel(start, 1);
    MoveStreamCodec.Encoded encoded = MoveStreamCodec.encode(model);
    assertEquals(RecordFile.TAG_CHESS960, encoded.tag());
    assertEquals(List.of(0xfffb, 0, 0xfffc, 0xffff), words(encoded.content()));
    GameMovesModel decoded = MoveStreamCodec.decode(encoded.tag(), encoded.content());
    assertEquals(start, decoded.root().position());
  }

  @Test
  void moveTreeFollowsTheSpecificationExample() throws Exception {
    GameMovesModel model =
        new PgnParser()
            .parseMoves("1. e4 c5 (1... c6 2. d4) (1... Nf6 2. e5) 2. Nf3 d6 (2... Nc6 3. Bb5) 3. d4");
    List<Integer> words = words(MoveStreamCodec.encode(model).content());

    // e4 c5 fffd Nf3 d6 fffd d4 ffff Nc6 Bb5 ffff c6 fffd d4 ffff Nf6 e5 ffff
    List<String> shape = new ArrayList<>();
    for (int w : words) {
      shape.add(
          switch (w) {
            case 0xfffc -> "moves";
            case 0xfffd -> "+";
            case 0xffff -> ";";
            default -> "m";
          });
    }
    assertEquals(
        List.of("moves", "m", "m", "+", "m", "m", "+", "m", ";", "m", "m", ";", "m", "+", "m", ";",
            "m", "m", ";"),
        shape);
    GameMovesModel decoded =
        MoveStreamCodec.decode(RecordFile.TAG_GAME, MoveStreamCodec.encode(model).content());
    assertEquals(model.toString(), decoded.toString());
  }

  @Test
  void emptyGame() {
    MoveStreamCodec.Encoded encoded = MoveStreamCodec.encode(new GameMovesModel());
    assertEquals(List.of(0xfffc, 0xffff), words(encoded.content()));
  }

  @Test
  void setUpPositionRoundTrips() {
    Position position =
        Position.fromString(
            "....k...\n........\n........\n........\n........\n........\n....P...\n....K...",
            Player.WHITE);
    GameMovesModel model = new GameMovesModel(position, 40);
    model.root().addMove(Chess.strToSqi("e2"), Chess.strToSqi("e4"));
    MoveStreamCodec.Encoded encoded = MoveStreamCodec.encode(model);
    GameMovesModel decoded = MoveStreamCodec.decode(encoded.tag(), encoded.content());
    assertEquals(model.root().position(), decoded.root().position());
    assertEquals(model.root().ply(), decoded.root().ply());
    assertEquals(model.toString(), decoded.toString());
    assertEquals(0xfffb, words(encoded.content()).get(0));
    assertEquals(40, words(encoded.content()).get(1));
  }

  @Test
  void placementWords() {
    assertEquals(0xc02d, MoveWords.placement(Stone.WHITE_KING, 0));
    assertEquals(0xc16d, MoveWords.placement(Stone.BLACK_KING, 0));
    assertEquals(0xc2ad, MoveWords.placement(Stone.WHITE_PAWN, Chess.strToSqi("a2")));
    assertEquals(0xc2dd, MoveWords.placement(Stone.BLACK_PAWN, Chess.strToSqi("a2")));
    assertEquals(0xc30c, MoveWords.placement(Stone.BLACK_PAWN, Chess.strToSqi("h7")));
    for (int w = MoveWords.FIRST_PLACEMENT; w < MoveWords.END_PLACEMENT; w++) {
      assertEquals(
          w, MoveWords.placement(MoveWords.placementStone(w), MoveWords.placementSquare(w)));
    }
  }

  @Test
  void checksum() {
    // Fewer than 8 bytes: the content itself, zero-padded, stored big-endian
    assertArrayEquals(
        new byte[] {0, 0, 0, 0, 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff},
        RecordFile.checksum(new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xff, 0x7f}));
    // 17 bytes: runs of 2, the last byte ignored
    byte[] content = new byte[17];
    for (int i = 0; i < 17; i++) {
      content[i] = (byte) (i + 1);
    }
    byte[] expected = new byte[8];
    for (int i = 0; i < 8; i++) {
      expected[7 - i] = (byte) (content[2 * i] + content[2 * i + 1]);
    }
    assertArrayEquals(expected, RecordFile.checksum(content));
  }

  private static List<Integer> words(byte[] content) {
    ByteBuffer buf = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
    List<Integer> words = new ArrayList<>();
    while (buf.hasRemaining()) {
      words.add(buf.getShort() & 0xFFFF);
    }
    return words;
  }
}
