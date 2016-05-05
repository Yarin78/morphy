package yarin.cbhlib;

import java.util.HashMap;

public enum MovePrefix {
    Nothing(0),
    EditorialAnnotation(0x91),
    BetterIs(0x8E),
    WorseIs(0x8F),
    EquivalentIs(0x90),
    WithTheIdea(0x8C),
    DirectedAgainst(0x8D);

    private static HashMap<Integer, MovePrefix> reverseMap = new HashMap<>();

    static {
        for (MovePrefix prefix : MovePrefix.values()) {
            reverseMap.put(prefix.getValue(), prefix);
        }
    }


    private int value;

    MovePrefix(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static MovePrefix decode(int value) {
        return reverseMap.get(value);
    }
}
