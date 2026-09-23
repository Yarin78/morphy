package se.yarin.morphy.cli.columns;

public class AnnotatorColumn implements GameColumn {
  @Override
  public String getHeader() {
    return "Annotator";
  }

  @Override
  public int width() {
    return 20;
  }

  @Override
  public String getValue(GameRow row) {
    return row.dto().annotator() == null ? "" : orEmpty(row.dto().annotator().name());
  }

  private static String orEmpty(String s) {
    return s == null ? "" : s;
  }

  @Override
  public String getId() {
    return "annotator";
  }
}
