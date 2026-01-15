package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Game;

public class SourceColumn implements GameColumn {
  @Override
  public String getHeader() {
    return "Source";
  }

  @Override
  public int width() {
    return 20;
  }

  @Override
  public String getValue(Game game) {
    return game.source().title();
  }

  @Override
  public String getId() {
    return "source";
  }
}
