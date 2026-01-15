package se.yarin.chess;

public enum GameResult {
  BLACK_WINS, // 0-1
  DRAW, // 1/2-1/2
  WHITE_WINS, // 1-0
  NOT_FINISHED, // Line
  WHITE_WINS_ON_FORFEIT, // +:-
  DRAW_ON_FORFEIT, // =:=
  BLACK_WINS_ON_FORFEIT, // -:+
  BOTH_LOST; // 0-0

  @Override
  public String toString() {
    return switch (this) {
      case BLACK_WINS -> "0-1";
      case DRAW -> "1/2-1/2";
      case WHITE_WINS -> "1-0";
      case NOT_FINISHED -> "*";
      case WHITE_WINS_ON_FORFEIT -> "+:-";
      case DRAW_ON_FORFEIT -> "=:=";
      case BLACK_WINS_ON_FORFEIT -> "-:+";
      case BOTH_LOST -> "0-0";
    };
  }
}
