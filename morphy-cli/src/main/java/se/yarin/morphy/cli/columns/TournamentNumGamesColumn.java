package se.yarin.morphy.cli.columns;

public class TournamentNumGamesColumn implements TournamentColumn {
  @Override
  public String getHeader() {
    return "Count";
  }

  @Override
  public String getTournamentValue(TournamentRow row) {
    Integer count = row.dto().gameCount();
    return String.format("%4d ", count == null ? 0 : count);
  }

  @Override
  public String getTournamentId() {
    return "count";
  }
}
