package se.yarin.morphy.cli.columns;

import se.yarin.chess.Date;

public class TournamentYearColumn extends TournamentBaseColumn {
  @Override
  public String getHeader() {
    return "Year";
  }

  @Override
  public String getTournamentValue(TournamentRow row) {
    Date date = row.dto().startDate();
    int year = date == null ? 0 : date.year();
    return year == 0 ? "????" : String.format("%4d", year);
  }

  @Override
  public String getId() {
    return "tournament-year";
  }
}
