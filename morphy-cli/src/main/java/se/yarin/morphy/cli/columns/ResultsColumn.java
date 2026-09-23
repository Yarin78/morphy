package se.yarin.morphy.cli.columns;

import se.yarin.chess.GameResult;

public class ResultsColumn implements GameColumn {
  @Override
  public String getHeader() {
    return "Res";
  }

  @Override
  public String getValue(GameRow row) {
    String result;
    if ("text".equals(row.dto().type())) {
      result = "Txt";
    } else {
      result = row.dto().result().toString();
      if (row.dto().result() == GameResult.DRAW) {
        result = "½-½";
      } else if (row.dto().result() == GameResult.NOT_FINISHED) {
        result = row.dto().lineEvaluation().toASCIIString();
      }
    }
    return result;
  }

  @Override
  public String getId() {
    return "result";
  }
}
