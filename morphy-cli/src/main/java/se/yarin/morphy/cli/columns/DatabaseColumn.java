package se.yarin.morphy.cli.columns;

public class DatabaseColumn implements GameColumn, TournamentColumn {
  @Override
  public String getHeader() {
    return "Database";
  }

  @Override
  public int width() {
    return 30;
  }

  @Override
  public int marginLeft() {
    return 1;
  }

  @Override
  public int marginRight() {
    return 1;
  }

  @Override
  public boolean trimValueToWidth() {
    return true;
  }

  @Override
  public String getTournamentValue(TournamentRow row) {
    String name = row.databaseName();
    return name == null ? "" : name;
  }

  @Override
  public String getValue(GameRow row) {
    String name = row.databaseName();
    return name == null ? "" : name;
  }

  @Override
  public String getTournamentId() {
    return "database";
  }

  @Override
  public String getId() {
    return "database";
  }
}
