package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Game;

public class GameVersionColumn implements GameColumn {

  @Override
  public String getHeader() {
    return "Vers";
  }

  @Override
  public String getValue(Game game) {
    return String.format("%4d", game.gameVersion());
  }

  @Override
  public String getId() {
    return "version";
  }
}
