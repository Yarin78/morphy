package se.yarin.chess;

public enum MoveComment implements Symbol {
    NOTHING,
    GOOD_MOVE,
    BAD_MOVE,
    EXCELLENT_MOVE,
    BLUNDER,
    INTERESTING_MOVE,
    DUBIOUS_MOVE,
    ZUGZWANG,
    ONLY_MOVE;

    public String toASCIIString() {
        switch (this) {
            case NOTHING:
                return "";
            case GOOD_MOVE:
                return "!";
            case BAD_MOVE:
                return "?";
            case EXCELLENT_MOVE:
                return "!!";
            case BLUNDER:
                return "??";
            case INTERESTING_MOVE:
                return "!?";
            case DUBIOUS_MOVE:
                return "?!";
            case ZUGZWANG:
                return "zugzwang";
            case ONLY_MOVE:
                return "only move";
        }
        return "";
    }

    public String toUnicodeString() {
        switch (this) {
            case NOTHING:
                return "";
            case GOOD_MOVE:
                return "!";
            case BAD_MOVE:
                return "?";
            case EXCELLENT_MOVE:
                return "!!";
            case BLUNDER:
                return "??";
            case INTERESTING_MOVE:
                return "!?";
            case DUBIOUS_MOVE:
                return "?!";
            case ZUGZWANG:
                return "\u0298";
            case ONLY_MOVE:
                return "\u25A1";
        }
        return "";
    }
}
