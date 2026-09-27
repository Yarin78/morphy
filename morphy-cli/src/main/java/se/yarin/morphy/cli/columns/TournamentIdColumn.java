package se.yarin.morphy.cli.columns;

public class TournamentIdColumn implements TournamentColumn {
  @Override
  public String getHeader() {
    return "    #";
  }

  @Override
  public int marginRight() {
    return 2;
  }

  @Override
  public String getTournamentValue(TournamentRow row) {
    return String.format("%5d", row.dto().id());
  }

  @Override
  public String getTournamentId() {
    return "id";
  }
}
