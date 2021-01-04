package se.yarin.cbhlib.games;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FinalMaterial {
    private final int numPawns;
    private final int numQueens;
    private final int numKnights;
    private final int numBishops;
    private final int numRooks;

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
        int value = (material.getNumPawns() & 15) << 12;
        value += Math.max(material.getNumQueens(), 7) << 9;
        value += Math.max(material.getNumKnights(), 7) << 6;
        value += Math.max(material.getNumBishops(), 7) << 3;
        value += Math.max(material.getNumRooks(), 7);
        return value;
    }
}
