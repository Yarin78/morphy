package yarin.cbhlib;

import java.util.HashMap;

/**
 * Created by yarin on 03/05/16.
 */
public enum LineEvaluation {
    NoEvaluation(0x00),
    WhiteHasDecisiveAdvantage(0x12),
    WhiteHasClearAdvantage(0x10),
    WhiteHasSlightAdvantage(0x0E),
    Equal(0x0B),
    Unclear(0x0D),
    BlackHasSlightAdvantage(0x0F),
    BlackHasClearAdvantage(0x11),
    BlackHasDecisiveAdvantage(0x13),
    TheoreticalNovelty(0x92),
    WithCompensation(0x2C),
    WithCounterplay(0x84),
    WithInitiative(0x24),
    WithAttack(0x28),
    DevelopmentAdvantage(0x20),
    ZeitNot(0x8A);

    private static HashMap<Integer, LineEvaluation> reverseMap = new HashMap<>();

    static {
        for (LineEvaluation eval : LineEvaluation.values()) {
            reverseMap.put(eval.getValue(), eval);
        }
    }

    private int value;

    LineEvaluation(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static LineEvaluation decode(int value) {
        return reverseMap.get(value);
    }
}
