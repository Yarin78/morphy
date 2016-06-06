package yarin.cbhlib;

import java.util.HashMap;

/**
 * Created by yarin on 03/05/16.
 */
public enum LineEvaluation {
    NoEvaluation(0x00, ""),
    WhiteHasDecisiveAdvantage(0x12, "+-"),
    WhiteHasClearAdvantage(0x10, "\u00B1"),
    WhiteHasSlightAdvantage(0x0E, "\u2A72"),
    Equal(0x0B, "="),
    Unclear(0x0D, "\u221E"),
    BlackHasSlightAdvantage(0x0F, "\u2a71"),
    BlackHasClearAdvantage(0x11, "\u2213"),
    BlackHasDecisiveAdvantage(0x13, "-+"),
    TheoreticalNovelty(0x92, "N"),
    WithCompensation(0x2C, "\u221E="),
    WithCounterplay(0x84, "\u21C4"),
    WithInitiative(0x24, "\u2191"),
    WithAttack(0x28, "\u2192"),
    DevelopmentAdvantage(0x20, "\u21BB"),
    ZeitNot(0x8A, "\u2295");

    private static HashMap<Integer, LineEvaluation> reverseMap = new HashMap<>();

    static {
        for (LineEvaluation eval : LineEvaluation.values()) {
            reverseMap.put(eval.getValue(), eval);
        }
    }

    private int value;
    private String symbol;

    LineEvaluation(int value, String symbol) {
        this.value = value;
        this.symbol = symbol;
    }

    public int getValue() {
        return value;
    }

    public String getSymbol() { return symbol; }

    public static LineEvaluation decode(int value) {
        return reverseMap.get(value);
    }
}
