package se.yarin.morphy.cli.columns;

import se.yarin.chess.Date;

public class TournamentDateColumn extends TournamentBaseColumn {
  @Override
  public String getHeader() {
    return "Start date";
  }

  @Override
  public int width() {
    return 10;
  }

  @Override
  public String getTournamentValue(TournamentRow row) {
    Date date = row.dto().startDate();
    return date == null ? "" : date.toPrettyString();
  }

  @Override
  public String getId() {
    return "tournament-date";
  }
}
