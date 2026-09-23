package se.yarin.morphy.cli.columns;

public class DateColumn implements GameColumn {
  @Override
  public String getHeader() {
    return "Date";
  }

  @Override
  public int width() {
    return 10;
  }

  @Override
  public String getValue(GameRow row) {
    return row.dto().date().toPrettyString();
  }

  @Override
  public String getId() {
    return "date";
  }
}
