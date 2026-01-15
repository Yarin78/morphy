package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Database;
import se.yarin.morphy.entities.Tournament;

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
  public String getTournamentValue(Database database, Tournament tournament) {
    return tournament.date().toPrettyString();
  }

  @Override
  public String getId() {
    return "tournament-date";
  }
}
