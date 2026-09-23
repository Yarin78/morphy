package se.yarin.morphy.cli.columns;

import se.yarin.morphy.DatabaseCbh;
import se.yarin.morphy.entities.Tournament;

public class TournamentCompleteColumn implements TournamentColumn {
  @Override
  public String getHeader() {
    return "Complete";
  }

  @Override
  public String getTournamentValue(DatabaseCbh db, Tournament tournament) {
    return tournament.complete() ? "Yes" : "-";
  }

  @Override
  public String getTournamentId() {
    return "complete";
  }
}
