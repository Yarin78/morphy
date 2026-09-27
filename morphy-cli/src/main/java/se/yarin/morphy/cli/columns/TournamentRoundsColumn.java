package se.yarin.morphy.cli.columns;

public class TournamentRoundsColumn extends TournamentBaseColumn {
  @Override
  public String getHeader() {
    return "Rnds";
  }

  @Override
  public int width() {
    return 5;
  }

  @Override
  public String getId() {
    return "tournament-rounds";
  }

  @Override
  public String getTournamentValue(TournamentRow row) {
    Integer rounds = row.dto().rounds();
    return String.format("%3d", rounds == null ? 0 : rounds);
  }
}
