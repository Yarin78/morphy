package se.yarin.morphy.cli.columns;

import se.yarin.morphy.DatabaseCbh;
import se.yarin.morphy.entities.Tournament;

public class TournamentCategoryColumn extends TournamentBaseColumn {
  @Override
  public String getHeader() {
    return "Cat";
  }

  @Override
  public int width() {
    return 7;
  }

  @Override
  public String getTournamentValue(DatabaseCbh database, Tournament tournament) {
    return tournament.getCategoryRoman();
  }

  @Override
  public String getId() {
    return "tournament-category";
  }
}
