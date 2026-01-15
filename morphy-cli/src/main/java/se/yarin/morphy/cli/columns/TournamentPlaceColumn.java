package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Database;
import se.yarin.morphy.entities.Tournament;

public class TournamentPlaceColumn extends TournamentBaseColumn {
  @Override
  public String getHeader() {
    return "Place";
  }

  @Override
  public int width() {
    return 20;
  }

  @Override
  public String getId() {
    return "tournament-place";
  }

  @Override
  public String getTournamentValue(Database database, Tournament tournament) {
    return tournament.place();
  }
}
