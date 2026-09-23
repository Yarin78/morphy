package se.yarin.morphy.cli.columns;

public class NumMovesColumn implements GameColumn {
  @Override
  public String getHeader() {
    return "Mov";
  }

  @Override
  public String getValue(GameRow row) {
    String numMoves = "";
    if (!"text".equals(row.dto().type())) {
      Integer n = row.dto().noMoves();
      numMoves = (n != null && n > 0) ? n.toString() : "";
    }
    return String.format("%3s", numMoves);
  }

  @Override
  public String getId() {
    return "num-moves";
  }
}
