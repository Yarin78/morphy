package se.yarin.chess;

public enum MovePrefix implements Symbol {
    NOTHING,
    EDITORIAL_ANNOTATION,
    BETTER_IS,
    WORSE_IS,
    EQUIVALENT_IS,
    WITH_THE_IDEA,
    DIRECTED_AGAINST;

    public String toASCIIString() {
        switch (this) {
            case NOTHING:
                return "";
            case EDITORIAL_ANNOTATION:
                return "RR";
            case BETTER_IS:
                return "Better is...";
            case WORSE_IS:
                return "Worse is...";
            case EQUIVALENT_IS:
                return "Equivalent is...";
            case WITH_THE_IDEA:
                return "With the idea...";
            case DIRECTED_AGAINST:
                return "Directed against...";
        }
        return "";
    }

    public String toUnicodeString() {
        switch (this) {
            case NOTHING:
                return "";
            case EDITORIAL_ANNOTATION:
                return "RR";
            case BETTER_IS:
                return "\u2313";
            case WORSE_IS:
                return "\u2264";
            case EQUIVALENT_IS:
                return "=";
            case WITH_THE_IDEA:
                return "\u0394";
            case DIRECTED_AGAINST:
                return "\u2207";
        }
        return "";
    }
}
