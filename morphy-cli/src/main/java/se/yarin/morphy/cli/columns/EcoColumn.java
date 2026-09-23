package se.yarin.morphy.cli.columns;

public class EcoColumn implements GameColumn {
  @Override
  public String getHeader() {
    return "ECO";
  }

  @Override
  public String getValue(GameRow row) {
    String eco = row.dto().eco();
    if ("text".equals(row.dto().type()) || eco == null) {
      return "";
    }
    eco = eco.length() >= 3 ? eco.substring(0, 3) : eco;
    return eco.equals("???") ? "" : eco;
  }

  @Override
  public String getId() {
    return "eco";
  }
}
