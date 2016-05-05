package yarin.cbhlib;

import java.util.HashMap;

public enum MoveComment {
    Nothing(0),
    GoodMove(1),
    BadMove(2),
    ExcellentMove(3),
    Blunder(4),
    InterestingMove(5),
    DubiousMove(6),
    ZugZwang(24),
    OnlyMove(8);

    private static HashMap<Integer, MoveComment> reverseMap = new HashMap<>();

    static {
        for (MoveComment moveComment : MoveComment.values()) {
            reverseMap.put(moveComment.getValue(), moveComment);
        }
    }

    private int value;

    MoveComment(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static MoveComment decode(int value) {
        return reverseMap.get(value);
    }
}
