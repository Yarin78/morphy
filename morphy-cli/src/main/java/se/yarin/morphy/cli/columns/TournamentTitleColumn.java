package se.yarin.morphy.cli.columns;

import se.yarin.morphy.DatabaseCbh;
import se.yarin.morphy.entities.Tournament;

public class TournamentTitleColumn extends TournamentBaseColumn {
  @Override
  public String getHeader() {
    return "Tournament";
  }

  @Override
  public int width() {
    return 30;
  }

  @Override
  public int marginLeft() {
    return 2;
  }

  @Override
  public int marginRight() {
    return 2;
  }

  @Override
  public String getTournamentValue(DatabaseCbh database, Tournament tournament) {
    return tournament.title();
  }

  @Override
  public String getId() {
    return "tournament";
  }

  @Override
  public String getTournamentId() {
    return "title";
  }
}
