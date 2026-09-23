package se.yarin.morphy.cli.columns;

public class GameIdColumn implements GameColumn {

  @Override
  public String getHeader() {
    return "  Game #";
  }

  @Override
  public int marginRight() {
    return 2;
  }

  @Override
  public String getValue(GameRow row) {
    return String.format("%8d", row.dto().id());
  }

  @Override
  public String getId() {
    return "id";
  }
}
