package se.yarin.morphy.cli.columns;

public class TournamentNationColumn extends TournamentBaseColumn {
  @Override
  public String getHeader() {
    return "Nation";
  }

  @Override
  public int width() {
    return 10;
  }

  @Override
  public String getId() {
    return "tournament-nation";
  }

  @Override
  public String getTournamentValue(TournamentRow row) {
    String nation = row.dto().nation();
    return nation == null ? "" : nation;
  }
}
