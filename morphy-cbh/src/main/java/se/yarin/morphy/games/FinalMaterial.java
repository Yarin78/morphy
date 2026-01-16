package se.yarin.morphy.games;

public record FinalMaterial(int numPawns, int numQueens, int numKnights, int numBishops, int numRooks) {
  public static FinalMaterial decode(int value) {
    int numPawns = (value >> 12) & 15;
    int numQueens = (value >> 9) & 7;
    int numKnights = (value >> 6) & 7;
    int numBishops = (value >> 3) & 7;
    int numRooks = (value >> 3) & 7;
    return new FinalMaterial(numPawns, numQueens, numKnights, numBishops, numRooks);
  }

  public static int encode(FinalMaterial material) {
    if (material == null) {
      return 0;
    }
    int value = (material.numPawns() & 15) << 12;
    value += Math.max(material.numQueens(), 7) << 9;
    value += Math.max(material.numKnights(), 7) << 6;
    value += Math.max(material.numBishops(), 7) << 3;
    value += Math.max(material.numRooks(), 7);
    return value;
  }
}
