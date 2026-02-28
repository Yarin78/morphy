package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Game;

public class VCSColumn implements GameColumn {
  @Override
  public String getHeader() {
    return "VCS";
  }

  @Override
  public String getValue(Game game) {
    return game.vcs();
  }

  @Override
  public String getId() {
    return "vcs";
  }
}
