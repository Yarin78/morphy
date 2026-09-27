package se.yarin.morphy.cli.columns;

import se.yarin.morphy.model.TournamentDto;

public abstract class TournamentBaseColumn implements GameColumn, TournamentColumn {

  public int width() {
    return getHeader().length();
  }

  public int marginLeft() {
    return 1;
  }

  public int marginRight() {
    return 1;
  }

  public boolean trimValueToWidth() {
    return true;
  }

  @Override
  public String getValue(GameRow row) {
    TournamentDto tournament = row.dto().tournament();
    if (tournament == null) {
      return "";
    }
    return getTournamentValue(new TournamentRow(tournament, row.databaseName()));
  }

  @Override
  public abstract String getTournamentValue(TournamentRow row);

  @Override
  public String getTournamentId() {
    String id = getId();
    assert id.startsWith("tournament-") : id;
    return id.substring(11);
  }
}
