package se.yarin.morphy.cb2.games;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.Piece;
import se.yarin.chess.Player;
import se.yarin.chess.Position;
import se.yarin.chess.Stone;

/**
 * The material a player has left at the end of a game, packed into one value: rooks in bits 0-2,
 * bishops 3-5, knights 6-8, queens 9-11 and pawns 12-15. A game record holds the greater of the
 * two players' values and then the lesser, not white's and black's.
 */
public final class FinalMaterial {
  private FinalMaterial() {}

  /** The packed material of one player in a position. */
  public static int of(@NotNull Position position, @NotNull Player player) {
    int rooks = 0, bishops = 0, knights = 0, queens = 0, pawns = 0;
    for (int sqi = 0; sqi < 64; sqi++) {
      Stone stone = position.stoneAt(sqi);
      if (stone.toPlayer() != player) {
        continue;
      }
      Piece piece = stone.toPiece();
      switch (piece) {
        case ROOK -> rooks++;
        case BISHOP -> bishops++;
        case KNIGHT -> knights++;
        case QUEEN -> queens++;
        case PAWN -> pawns++;
        default -> {}
      }
    }
    return Math.min(rooks, 7)
        | Math.min(bishops, 7) << 3
        | Math.min(knights, 7) << 6
        | Math.min(queens, 7) << 9
        | Math.min(pawns, 15) << 12;
  }

  /** The two values of a position as stored, the greater first. */
  public static int[] ordered(@NotNull Position position) {
    int white = of(position, Player.WHITE), black = of(position, Player.BLACK);
    return white >= black ? new int[] {white, black} : new int[] {black, white};
  }

  /** The material as text, e.g. {@code QRRBNPPPPP}. */
  public static @NotNull String toString(int value) {
    StringBuilder sb = new StringBuilder();
    sb.append("Q".repeat((value >> 9) & 7));
    sb.append("R".repeat(value & 7));
    sb.append("B".repeat((value >> 3) & 7));
    sb.append("N".repeat((value >> 6) & 7));
    sb.append("P".repeat((value >> 12) & 15));
    return sb.toString();
  }
}
