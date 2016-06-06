package yarin.cbhlib;

import java.util.HashMap;

public enum MovePrefix {
    Nothing(0, ""),
    EditorialAnnotation(0x91, "RR"),
    BetterIs(0x8E, "\u2313"), // TODO: This unicode character is not ideal
    WorseIs(0x8F, "\u2264"),
    EquivalentIs(0x90, "="),
//    WithTheIdea(0x8C, "\u2206"),
    WithTheIdea(0x8C, "\u0394"),
    DirectedAgainst(0x8D, "\u2207");

    private static HashMap<Integer, MovePrefix> reverseMap = new HashMap<>();

    static {
        for (MovePrefix prefix : MovePrefix.values()) {
            reverseMap.put(prefix.getValue(), prefix);
        }
    }


    private int value;
    private String symbol;

    MovePrefix(int value, String symbol) {
        this.value = value;
        this.symbol = symbol;
    }

    public int getValue() {
        return value;
    }

    public String getSymbol() { return symbol; }

    public static MovePrefix decode(int value) {
        return reverseMap.get(value);
    }
}
