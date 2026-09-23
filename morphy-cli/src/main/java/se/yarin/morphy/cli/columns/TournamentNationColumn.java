package se.yarin.morphy.cli.columns;

import se.yarin.morphy.DatabaseCbh;
import se.yarin.morphy.entities.Tournament;

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
  public String getTournamentValue(DatabaseCbh database, Tournament tournament) {
    return tournament.nation().getName();
  }
}
