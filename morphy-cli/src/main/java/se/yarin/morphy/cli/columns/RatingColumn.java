package se.yarin.morphy.cli.columns;

public class RatingColumn implements GameColumn {

  private final boolean isWhite;

  public RatingColumn(boolean isWhite) {
    this.isWhite = isWhite;
  }

  @Override
  public String getHeader() {
    return "";
  }

  @Override
  public int marginRight() {
    return 2;
  }

  @Override
  public String getValue(GameRow row) {
    if ("text".equals(row.dto().type())) {
      return "";
    }
    Integer rating = isWhite ? row.dto().whiteElo() : row.dto().blackElo();
    String elo = rating == null ? "" : rating.toString();
    return String.format("%4s", elo);
  }

  @Override
  public String getId() {
    return "rating";
  }

  @Override
  public int width() {
    return 4;
  }
}
