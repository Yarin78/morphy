package se.yarin.morphy.cli.columns;

public class TournamentCompleteColumn implements TournamentColumn {
  @Override
  public String getHeader() {
    return "Complete";
  }

  @Override
  public String getTournamentValue(TournamentRow row) {
    return Boolean.TRUE.equals(row.dto().complete()) ? "Yes" : "-";
  }

  @Override
  public String getTournamentId() {
    return "complete";
  }
}
