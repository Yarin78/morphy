package se.yarin.morphy.cli.columns;

public class TournamentTimeControlColumn extends TournamentBaseColumn {
  @Override
  public String getHeader() {
    return "Time";
  }

  @Override
  public int width() {
    return 6;
  }

  @Override
  public String getId() {
    return "tournament-time";
  }

  @Override
  public String getTournamentValue(TournamentRow row) {
    String timeControl = row.dto().timeControl();
    return timeControl == null ? "" : timeControl;
  }
}
