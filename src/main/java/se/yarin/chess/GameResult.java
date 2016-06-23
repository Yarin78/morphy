package se.yarin.chess;

public enum GameResult {
    WHITE_WINS,
    DRAW,
    BLACK_WINS,
    NOT_FINISHED;

    @Override
    public String toString() {
        switch (this) {
            case WHITE_WINS:   return "1-0";
            case DRAW:         return "1/2-1/2";
            case BLACK_WINS:   return "0-1";
            case NOT_FINISHED: return "*";
        }
        return "";
    }
}
