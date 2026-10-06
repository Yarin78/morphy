package se.yarin.morphy.positions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Move;
import se.yarin.chess.Position;

/** How the index identifies a position, and a move played from it. */
public final class PositionKeys {
  private PositionKeys() {}

  /** The move "played" from the last position of a game: none, the game ended there. */
  public static final int GAME_ENDED = 0xFFFF;

  /** What {@link #moveAfter} returns for a position the game never reaches. */
  public static final int NOT_REACHED = -1;

  private static final int NULL_MOVE = 0x7FFF;

  /**
   * The position's key: its 64-bit Zobrist hash, in which the en passant file only counts when a
   * pawn can take en passant, so transpositions meet.
   */
  public static long hash(@NotNull Position position) {
    return position.getZobristHashLo();
  }

  /** A move as 16 bits: the from and to squares, and the piece promoted to. */
  public static int moveCode(@NotNull Move move) {
    if (move.isNullMove()) {
      return NULL_MOVE;
    }
    int promotion =
        switch (move.promotionStone().toPiece()) {
          case KNIGHT -> 1;
          case BISHOP -> 2;
          case ROOK -> 3;
          case QUEEN -> 4;
          default -> 0;
        };
    return move.fromSqi() | (move.toSqi() << 6) | (promotion << 12);
  }

  /** The legal move in a position with a code, or null if there is none. */
  public static @Nullable Move move(@NotNull Position position, int code) {
    if (code == NULL_MOVE) {
      return Move.nullMove(position);
    }
    for (Move move : position.generateAllLegalMoves()) {
      if (moveCode(move) == code) {
        return move;
      }
    }
    return null;
  }

  /**
   * The move a game's main line plays from a position, the first time it's reached.
   *
   * @return the move's code, {@link #GAME_ENDED} if the game ends there, or {@link #NOT_REACHED}
   */
  public static int moveAfter(@NotNull GameMovesModel moves, long hash) {
    GameMovesModel.Node node = moves.root();
    while (true) {
      boolean reached = hash(node.position()) == hash;
      if (!node.hasMoves()) {
        return reached ? GAME_ENDED : NOT_REACHED;
      }
      GameMovesModel.Node next = node.mainNode();
      if (reached) {
        return moveCode(next.lastMove());
      }
      node = next;
    }
  }
}
