package se.yarin.chess;

public enum LineEvaluation implements Symbol {
    NO_EVALUATION,
    WHITE_DECISIVE_ADVANTAGE,
    WHITE_CLEAR_ADVANTAGE,
    WHITE_SLIGHT_ADVANTAGE,
    EQUAL,
    UNCLEAR,
    BLACK_SLIGHT_ADVANTAGE,
    BLACK_CLEAR_ADVANTAGE,
    BLACK_DECISIVE_ADVANTAGE,
    THEORETICAL_NOVELTY,
    WITH_COMPENSATION,
    WITH_COUNTERPLAY,
    WITH_INITIATIVE,
    WITH_ATTACK,
    DEVELOPMENT_ADVANTAGE,
    ZEITNOT;

    public String toASCIIString() {
        switch (this) {
            case NO_EVALUATION: return "";
            case WHITE_DECISIVE_ADVANTAGE: return "+-";
            case WHITE_CLEAR_ADVANTAGE: return "+/-";
            case WHITE_SLIGHT_ADVANTAGE: return "+/=";
            case EQUAL: return "=";
            case UNCLEAR: return "unclear";
            case BLACK_SLIGHT_ADVANTAGE: return "=/+";
            case BLACK_CLEAR_ADVANTAGE: return "-/+";
            case BLACK_DECISIVE_ADVANTAGE: return "-+";
            case THEORETICAL_NOVELTY: return "N";
            case WITH_COMPENSATION: return "w/ comp";
            case WITH_COUNTERPLAY: return "w/ counter";
            case WITH_INITIATIVE: return "w/ initiative";
            case WITH_ATTACK: return "w/ attack";
            case DEVELOPMENT_ADVANTAGE: return "dev adv";
            case ZEITNOT: return "zeitnot";
        }
        return "";
    }

    public String toUnicodeString() {
        switch (this) {
            case NO_EVALUATION: return "";
            case WHITE_DECISIVE_ADVANTAGE: return "+-";
            case WHITE_CLEAR_ADVANTAGE: return "\u00B1";
            case WHITE_SLIGHT_ADVANTAGE: return "\u2A72";
            case EQUAL: return "=";
            case UNCLEAR: return "\u221E";
            case BLACK_SLIGHT_ADVANTAGE: return "\u2a71";
            case BLACK_CLEAR_ADVANTAGE: return "\u2213";
            case BLACK_DECISIVE_ADVANTAGE: return "-+";
            case THEORETICAL_NOVELTY: return "N";
            case WITH_COMPENSATION: return "\u221E=";
            case WITH_COUNTERPLAY: return "\u21C4";
            case WITH_INITIATIVE: return "\u2191";
            case WITH_ATTACK: return "\u2192";
            case DEVELOPMENT_ADVANTAGE: return "\u21BB";
            case ZEITNOT: return "\u2295";
        }
        return "";
    }
}
