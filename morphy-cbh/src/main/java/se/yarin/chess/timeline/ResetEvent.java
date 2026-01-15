package se.yarin.chess.timeline;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.NavigableGameModel;

/** Event that resets the game model. */
public class ResetEvent extends GameEvent {
  @Override
  public void apply(@NotNull NavigableGameModel model) throws GameEventException {
    model.reset();
  }
}
