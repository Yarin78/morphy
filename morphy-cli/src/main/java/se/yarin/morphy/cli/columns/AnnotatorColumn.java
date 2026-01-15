package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Game;

public class AnnotatorColumn implements GameColumn {
  @Override
  public String getHeader() {
    return "Annotator";
  }

  @Override
  public int width() {
    return 20;
  }

  @Override
  public String getValue(Game game) {
    return game.annotator().name();
  }

  @Override
  public String getId() {
    return "annotator";
  }
}
