package se.yarin.morphy.cli.columns;

public class RoundColumn implements GameColumn {
  @Override
  public String getHeader() {
    return "Rnd";
  }

  @Override
  public int width() {
    return 4;
  }

  @Override
  public String getValue(GameRow row) {
    Integer round = row.dto().round();
    Integer subRound = row.dto().subRound();
    if (round == null) {
      return "";
    }
    if (subRound == null) {
      return String.format("%4d", round);
    }
    return String.format("%2d.%d", round, subRound);
  }

  @Override
  public String getId() {
    return "round";
  }
}
