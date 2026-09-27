package se.yarin.morphy.cli.columns;

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
  public String getTournamentValue(TournamentRow row) {
    String category = row.dto().categoryRoman();
    return category == null ? "" : category;
  }

  @Override
  public String getId() {
    return "tournament-category";
  }
}
