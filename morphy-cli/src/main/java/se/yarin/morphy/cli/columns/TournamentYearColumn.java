package se.yarin.morphy.cli.columns;

import se.yarin.morphy.DatabaseCbh;
import se.yarin.morphy.entities.Tournament;

public class TournamentYearColumn extends TournamentBaseColumn {
  @Override
  public String getHeader() {
    return "Year";
  }

  @Override
  public String getTournamentValue(DatabaseCbh database, Tournament tournament) {
    int year = tournament.date().year();
    return year == 0 ? "????" : String.format("%4d", year);
  }

  @Override
  public String getId() {
    return "tournament-year";
  }
}
