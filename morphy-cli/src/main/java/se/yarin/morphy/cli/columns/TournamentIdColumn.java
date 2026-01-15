package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Database;
import se.yarin.morphy.entities.Tournament;

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
  public String getTournamentValue(Database db, Tournament tournament) {
    return String.format("%5d", tournament.id());
  }

  @Override
  public String getTournamentId() {
    return "id";
  }
}
