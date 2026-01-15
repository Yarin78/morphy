package se.yarin.morphy.games;

import org.immutables.value.Value;

@Value.Immutable
public abstract class FinalMaterial {
  @Value.Parameter
  public abstract int numPawns();

  @Value.Parameter
  public abstract int numQueens();

  @Value.Parameter
  public abstract int numKnights();

  @Value.Parameter
  public abstract int numBishops();

  @Value.Parameter
  public abstract int numRooks();

  public static FinalMaterial decode(int value) {
    int numPawns = (value >> 12) & 15;
    int numQueens = (value >> 9) & 7;
    int numKnights = (value >> 6) & 7;
    int numBishops = (value >> 3) & 7;
    int numRooks = (value >> 3) & 7;
    return ImmutableFinalMaterial.of(numPawns, numQueens, numKnights, numBishops, numRooks);
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
