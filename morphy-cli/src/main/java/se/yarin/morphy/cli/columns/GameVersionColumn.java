package se.yarin.morphy.cli.columns;

public class GameVersionColumn implements GameColumn {

  @Override
  public String getHeader() {
    return "Vers";
  }

  @Override
  public String getValue(GameRow row) {
    Integer version = row.dto().gameVersion();
    return String.format("%4d", version == null ? 0 : version);
  }

  @Override
  public String getId() {
    return "version";
  }
}
