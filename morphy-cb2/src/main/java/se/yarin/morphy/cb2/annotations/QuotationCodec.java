package se.yarin.morphy.cb2.annotations;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.GameHeaderModel;
import se.yarin.chess.GameResult;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Move;
import se.yarin.chess.Piece;
import se.yarin.morphy.cb2.TextEncoding;
import se.yarin.morphy.cb2.games.Dates;
import se.yarin.morphy.chessbase.annotations.GameQuotationAnnotation;

/**
 * Reads and writes a game quotation annotation. Much of its layout is unknown, so a quotation
 * keeps the bytes it was read from as its {@link GameQuotationAnnotation#encoding() encoding} and
 * is written back from them; a quotation that didn't come from a v2 database can't be written.
 *
 * <p>The known parts: a 10-byte preamble whose second field is 2 when the moves are included, six
 * strings (white's last and first name, black's last and first name, site, event) each a length
 * byte counting a terminating zero, the text and the zero; 35 bytes starting with the date and
 * ending with the result; 44
 * bytes; two rating types, each 5 bytes and an {@code int}-prefixed string; 29 bytes; an {@code
 * int} number of moves, 5 bytes per move starting with its origin and destination squares; and an
 * {@code int} 0.
 */
final class QuotationCodec {
  private static final Logger log = LoggerFactory.getLogger(QuotationCodec.class);

  /** The bytes of a quotation as stored in a v2 database. */
  record Encoding(byte @NotNull [] data) {
    @Override
    public boolean equals(Object o) {
      return o instanceof Encoding that && Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(data);
    }
  }

  private QuotationCodec() {}

  static boolean canWrite(@NotNull GameQuotationAnnotation quotation) {
    return quotation.encoding() instanceof Encoding;
  }

  static int size(@NotNull GameQuotationAnnotation quotation) {
    return quotation.encoding() instanceof Encoding e ? e.data().length : 0;
  }

  static void write(@NotNull ByteBuffer buf, @NotNull GameQuotationAnnotation quotation) {
    buf.put(((Encoding) quotation.encoding()).data());
  }

  static @NotNull GameQuotationAnnotation read(@NotNull ByteBuffer buf) {
    int start = buf.position();
    boolean withMoves = buf.getShort(start + 1) == 2;
    int pos = start + 10;
    String[] strings = new String[6];
    for (int i = 0; i < 6; i++) {
      int length = buf.get(pos) & 0xFF;
      byte[] text = new byte[Math.max(0, length - 1)];
      buf.get(pos + 1, text);
      strings[i] = TextEncoding.decode(text);
      pos += 1 + length;
    }
    int date = buf.getInt(pos);
    int result = buf.get(pos + 34) & 0xFF;
    pos += 35 + 44;
    for (int i = 0; i < 2; i++) {
      pos += 5;
      pos += 4 + buf.getInt(pos);
    }
    pos += 29;
    int moveCount = buf.getInt(pos);
    int movesStart = pos + 4;
    int end = movesStart + 5 * moveCount + 4;
    byte[] data = new byte[end - start];
    buf.get(start, data);
    buf.position(end);

    GameHeaderModel header = new GameHeaderModel();
    header.setWhite(name(strings[0], strings[1]));
    header.setBlack(name(strings[2], strings[3]));
    header.setEventSite(strings[4]);
    header.setEvent(strings[5]);
    header.setDate(Dates.decode(date));
    if (result < GameResult.values().length) {
      header.setResult(GameResult.values()[result]);
    }

    Encoding encoding = new Encoding(data);
    int movesOffset = movesStart - start;
    return new GameQuotationAnnotation(
        header,
        0,
        withMoves ? () -> decodeMoves(encoding.data(), movesOffset, moveCount) : null,
        encoding);
  }

  private static String name(String last, String first) {
    return first.isEmpty() ? last : last + ", " + first;
  }

  /**
   * The main line of a quotation. Each move is its origin and destination square followed by 3
   * bytes of unknown meaning, so a promotion is taken to be to a queen.
   */
  private static GameMovesModel decodeMoves(byte[] data, int offset, int count) {
    ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    GameMovesModel moves = new GameMovesModel();
    GameMovesModel.Node node = moves.root();
    for (int i = 0; i < count; i++) {
      int from = buf.get(offset + 5 * i) & 0xFF, to = buf.get(offset + 5 * i + 1) & 0xFF;
      Move move = null;
      for (Move candidate : node.position().generateAllLegalMoves()) {
        if (candidate.fromSqi() == from && candidate.toSqi() == to) {
          if (move == null || candidate.promotionStone().toPiece() == Piece.QUEEN) {
            move = candidate;
          }
        }
      }
      if (move == null) {
        log.warn("Move {} of a game quotation is not legal; the quotation's moves end there", i);
        break;
      }
      node = node.addMove(move);
    }
    return moves;
  }
}
