package se.yarin.morphy.cli.columns;

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
  public String getTournamentValue(TournamentRow row) {
    String title = row.dto().title();
    return title == null ? "" : title;
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
