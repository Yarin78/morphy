package se.yarin.morphy.games;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Position;
import se.yarin.chess.Stone;

public record FinalMaterial(
    int numPawns, int numQueens, int numKnights, int numBishops, int numRooks)
    implements Comparable<FinalMaterial> {

  @Override
  public int compareTo(FinalMaterial other) {
    // Side with most pawns come first
    int cmp = this.numPawns - other.numPawns;
    if (cmp != 0) return -cmp;

    // Then most queens
    cmp = this.numQueens - other.numQueens;
    if (cmp != 0) return -cmp;

    // Then most knights
    cmp = this.numKnights - other.numKnights;
    if (cmp != 0) return -cmp;

    // Then most bishops
    cmp = this.numBishops - other.numBishops;
    if (cmp != 0) return -cmp;

    // Then most rooks
    cmp = this.numRooks - other.numRooks;
    return -cmp;
  }

  @Override
  public @NotNull String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Q".repeat(numQueens));
    sb.append("R".repeat(numRooks));
    sb.append("B".repeat(numBishops));
    sb.append("N".repeat(numKnights));
    if (numPawns > 0) {
      sb.append(numPawns).append("P");
    }
    return sb.toString();
  }

  public static FinalMaterial sum(FinalMaterial player1, FinalMaterial player2) {
    return new FinalMaterial(
        player1.numPawns + player2.numPawns,
        player1.numQueens + player2.numQueens,
        player1.numKnights + player2.numKnights,
        player1.numBishops + player2.numBishops,
        player1.numRooks + player2.numRooks);
  }

  public static FinalMaterial decode(int value) {
    int numPawns = (value >> 12) & 15;
    int numQueens = (value >> 9) & 7;
    int numKnights = (value >> 6) & 7;
    int numBishops = (value >> 3) & 7;
    int numRooks = value & 7;
    return new FinalMaterial(numPawns, numQueens, numKnights, numBishops, numRooks);
  }

  public static int encode(FinalMaterial material) {
    if (material == null) {
      return 0;
    }
    int value = (material.numPawns() & 15) << 12;
    value += Math.min(material.numQueens(), 7) << 9;
    value += Math.min(material.numKnights(), 7) << 6;
    value += Math.min(material.numBishops(), 7) << 3;
    value += Math.min(material.numRooks(), 7);
    return value;
  }

  /**
   * Calculates the final material in a game by following the main line to the end.
   *
   * @param model the game moves model
   * @return a result containing the final material for white, black, and both combined
   */
  public static @NotNull Result calculate(@NotNull GameMovesModel model) {
    // Navigate to the end of the main line
    GameMovesModel.Node currentNode = model.root();
    while (currentNode.mainNode() != null) {
      currentNode = currentNode.mainNode();
    }

    // Get the final position
    Position finalPosition = currentNode.position();

    // Count pieces for each side
    int whitePawns = 0, whiteQueens = 0, whiteKnights = 0, whiteBishops = 0, whiteRooks = 0;
    int blackPawns = 0, blackQueens = 0, blackKnights = 0, blackBishops = 0, blackRooks = 0;

    for (int sqi = 0; sqi < 64; sqi++) {
      Stone stone = finalPosition.stoneAt(sqi);
      switch (stone) {
        case WHITE_PAWN -> whitePawns++;
        case WHITE_QUEEN -> whiteQueens++;
        case WHITE_KNIGHT -> whiteKnights++;
        case WHITE_BISHOP -> whiteBishops++;
        case WHITE_ROOK -> whiteRooks++;
        case BLACK_PAWN -> blackPawns++;
        case BLACK_QUEEN -> blackQueens++;
        case BLACK_KNIGHT -> blackKnights++;
        case BLACK_BISHOP -> blackBishops++;
        case BLACK_ROOK -> blackRooks++;
        default -> {} // NO_STONE or KING (not counted in FinalMaterial)
      }
    }

    FinalMaterial white =
        new FinalMaterial(whitePawns, whiteQueens, whiteKnights, whiteBishops, whiteRooks);
    FinalMaterial black =
        new FinalMaterial(blackPawns, blackQueens, blackKnights, blackBishops, blackRooks);

    if (white.compareTo(black) < 0) {
      return new Result(white, black);
    } else {
      return new Result(black, white);
    }
  }

  public boolean isEmpty() {
      return numPawns + numQueens + numRooks + numBishops + numKnights == 0;
  }

  /**
   * Result of calculating final material in a game.
   *
   * @param player1 material for white pieces
   * @param player2 material for black pieces
   */
  public record Result(@NotNull FinalMaterial player1, @NotNull FinalMaterial player2) {}
}
