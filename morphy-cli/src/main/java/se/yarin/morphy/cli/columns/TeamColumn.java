package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Team;

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
  public String getValue(Game game) {
    if (game.guidingText()) {
      return "";
    }
    Team team = isWhite ? game.whiteTeam() : game.blackTeam();
    return team == null ? "" : team.title();
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
