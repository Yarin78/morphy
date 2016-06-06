package yarin.cbhlib;

import java.util.HashMap;

public enum MoveComment {
    Nothing(0, ""),
    GoodMove(1, "!"),
    BadMove(2, "?"),
    ExcellentMove(3, "!!"),
    Blunder(4, "??"),
    InterestingMove(5, "!?"),
    DubiousMove(6, "?!"),
    ZugZwang2(22, "\u0298"),
    ZugZwang(24, "\u0298"),
    OnlyMove(8, "\u25A1");

    private static HashMap<Integer, MoveComment> reverseMap = new HashMap<>();

    static {
        for (MoveComment moveComment : MoveComment.values()) {
            reverseMap.put(moveComment.getValue(), moveComment);
        }
    }

    private int value;
    private String symbol;

    MoveComment(int value, String symbol) {
        this.value = value;
        this.symbol = symbol;
    }

    public int getValue() {
        return value;
    }

    public String getSymbol() { return symbol; }

    public static MoveComment decode(int value) {
        return reverseMap.get(value);
    }
}
