package se.yarin.morphy.cli.columns;

import se.yarin.morphy.model.TeamDto;

public class TeamColumn implements GameColumn {

  private final boolean isWhite;

  public TeamColumn(boolean isWhite) {
    this.isWhite = isWhite;
  }

  @Override
  public String getHeader() {
    return "Team";
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
    TeamDto team = isWhite ? row.dto().whiteTeam() : row.dto().blackTeam();
    return team == null || team.title() == null ? "" : team.title();
  }

  @Override
  public String getId() {
    return "team";
  }

  @Override
  public int width() {
    return 20;
  }
}
