package se.yarin.morphy.cli.update;

import se.yarin.chess.GameModel;

/** Sets a fixed GameTag on every game it's applied to. */
public class StaticTagUpdater implements GameUpdater {
  private final String tag;

  public StaticTagUpdater(String tag) {
    this.tag = tag;
  }

  @Override
  public boolean apply(GameModel model) {
    model.header().setGameTag(tag);
    return true;
  }
}
