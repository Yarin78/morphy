package se.yarin.morphy.cli.columns;

public class TournamentTypeColumn extends TournamentBaseColumn {
  @Override
  public String getHeader() {
    return "Type";
  }

  @Override
  public int width() {
    return 25;
  }

  @Override
  public String getId() {
    return "tournament-type";
  }

  @Override
  public String getTournamentValue(TournamentRow row) {
    String type = row.dto().typeCombined();
    return type == null ? "" : type;
  }
}
