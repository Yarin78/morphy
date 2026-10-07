package se.yarin.chess;

import org.jetbrains.annotations.NotNull;

/**
 * A move as a small number: its from and to squares and the piece promoted to, in 15 bits.
 * Castling is the king's move; a null move has a code of its own.
 */
public final class MoveCode {
  private MoveCode() {}

  /** The code of a null move. */
  public static final int NULL_MOVE = 0x7FFF;

  /** No move: the game ended. */
  public static final int NONE = -1;

  /** The code of a move. */
  public static int of(@NotNull Move move) {
    if (move.isNullMove()) {
      return NULL_MOVE;
    }
    return of(move.fromSqi(), move.toSqi(), move.promotionStone().toPiece());
  }

  /** The code of a move from a square to another, promoting to a piece or not. */
  public static int of(int fromSqi, int toSqi, @NotNull Piece promotion) {
    int p =
        switch (promotion) {
          case KNIGHT -> 1;
          case BISHOP -> 2;
          case ROOK -> 3;
          case QUEEN -> 4;
          default -> 0;
        };
    return fromSqi | (toSqi << 6) | (p << 12);
  }
}
