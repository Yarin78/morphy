package se.yarin.morphy.cli.columns;

public class SourceColumn implements GameColumn {
  @Override
  public String getHeader() {
    return "Source";
  }

  @Override
  public int width() {
    return 20;
  }

  @Override
  public String getValue(GameRow row) {
    return row.dto().source() == null ? "" : orEmpty(row.dto().source().title());
  }

  private static String orEmpty(String s) {
    return s == null ? "" : s;
  }

  @Override
  public String getId() {
    return "source";
  }
}
