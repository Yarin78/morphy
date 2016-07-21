package se.yarin.chess;

public enum GameResult {
    BLACK_WINS, // 0-1
    DRAW,  // 1/2-1/2
    WHITE_WINS,  // 1-0
    NOT_FINISHED, // Line
    WHITE_WINS_ON_FORFEIT, // +:-
    DRAW_ON_FORFEIT, // =:=
    BLACK_WINS_ON_FORFEIT, // -:+
    BOTH_LOST; // 0-0

    @Override
    public String toString() {
        switch (this) {
            case BLACK_WINS:   return "0-1";
            case DRAW:         return "1/2-1/2";
            case WHITE_WINS:   return "1-0";
            case NOT_FINISHED: return "*";
            case WHITE_WINS_ON_FORFEIT:   return "+:-";
            case DRAW_ON_FORFEIT:   return "=:=";
            case BLACK_WINS_ON_FORFEIT:   return "-:+";
            case BOTH_LOST:   return "0-0";
        }
        return "";
    }
}
