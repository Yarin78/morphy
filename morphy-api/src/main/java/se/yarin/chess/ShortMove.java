package se.yarin.chess;

import org.jetbrains.annotations.NotNull;

import static se.yarin.chess.Piece.NO_PIECE;

/**
 * Represents the minimum information about a chess move to be able to apply it on a position. Does
 * not contain enough information to display the move without knowing the position. ShortMove is
 * immutable.
 *
 * <p>Note: There may be different ways of representing castles, in which case equals/hashcode
 * doesn't work
 */
public record ShortMove(
    int fromSqi, int toSqi, Stone promotionStone, boolean longCastling, boolean shortCastling) {
  public ShortMove {
    // Skip validation for castling moves (which have special square values)
    if (!longCastling && !shortCastling) {
      if (fromSqi < 0 || fromSqi >= 64 || toSqi < 0 || toSqi >= 64) {
        // Check if the null move
        if (!(fromSqi == Chess.NO_SQUARE
            && toSqi == Chess.NO_SQUARE
            && promotionStone == Stone.NO_STONE)) {
          throw new IllegalArgumentException("Not a valid move");
        }
      }
    }
  }

  /**
   * Creates a new short move. To create a castling move, use {@link #shortCastles()} or {@link
   * #longCastles()} instead.
   *
   * @param fromSqi the from square index
   * @param toSqi to to square index
   */
  public ShortMove(int fromSqi, int toSqi) {
    this(fromSqi, toSqi, Stone.NO_STONE, false, false);
  }

  /**
   * Creates a new short move where a pawn promotes.
   *
   * @param fromSqi the from square index
   * @param toSqi to to square index
   * @param promotionStone the new stone after the promotion
   */
  public ShortMove(int fromSqi, int toSqi, Stone promotionStone) {
    this(fromSqi, toSqi, promotionStone, false, false);
  }

  /**
   * Creates a move representing long (queenside, a-side) castles
   *
   * @return a long castle move
   */
  public static ShortMove longCastles() {
    return new ShortMove(0, 0, Stone.NO_STONE, true, false);
  }

  /**
   * Creates a move representing short (kingside, h-side) castles
   *
   * @return a short castle move
   */
  public static ShortMove shortCastles() {
    return new ShortMove(0, 0, Stone.NO_STONE, false, true);
  }

  /**
   * Creates a null move
   *
   * @return a null move
   */
  public static ShortMove nullMove() {
    return new ShortMove(Chess.NO_SQUARE, Chess.NO_SQUARE);
  }

  public int fromCol() {
    return Chess.sqiToCol(fromSqi);
  }

  public int fromRow() {
    return Chess.sqiToRow(fromSqi);
  }

  public int toCol() {
    return Chess.sqiToCol(toSqi);
  }

  public int toRow() {
    return Chess.sqiToRow(toSqi);
  }

  public boolean isLongCastle() {
    return longCastling;
  }

  public boolean isShortCastle() {
    return shortCastling;
  }

  public Move toMove(@NotNull Position fromPosition) {
    if (shortCastling) {
      return Move.shortCastles(fromPosition);
    }
    if (longCastling) {
      return Move.longCastles(fromPosition);
    }
    return new Move(fromPosition, fromSqi(), toSqi(), promotionStone());
  }

  public boolean isNullMove() {
    return fromSqi == Chess.NO_SQUARE;
  }

  public boolean moveEquals(Move move) {
    if (move == null) return false;
    if (isShortCastle() || isLongCastle() || move.isShortCastle() || move.isLongCastle()) {
      return isShortCastle() == move.isShortCastle() && isLongCastle() == move.isLongCastle();
    }

    return fromSqi() == move.fromSqi()
        && toSqi() == move.toSqi()
        && promotionStone() == move.promotionStone();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(5);
    if (isNullMove()) {
      sb.append("----");
    } else if (shortCastling) {
      sb.append("O-O");
    } else if (longCastling) {
      sb.append("O-O-O");
    } else {
      sb.append(Chess.sqiToStr(fromSqi));
      sb.append(Chess.sqiToStr(toSqi));
      if (promotionStone.toPiece() != NO_PIECE) {
        sb.append('=').append(promotionStone.toPiece().toChar());
      }
    }
    return sb.toString();
  }
}
