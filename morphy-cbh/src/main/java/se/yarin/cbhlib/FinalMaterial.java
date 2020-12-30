package se.yarin.cbhlib;

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
}
