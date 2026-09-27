package se.yarin.morphy.cli.columns;

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
  public String getTournamentValue(TournamentRow row) {
    String place = row.dto().place();
    return place == null ? "" : place;
  }
}
