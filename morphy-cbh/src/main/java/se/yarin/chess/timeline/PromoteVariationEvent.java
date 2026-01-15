package se.yarin.chess.timeline;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.NavigableGameModel;

/** Event that promotes the current variation. */
public class PromoteVariationEvent extends GameEvent {
  @Override
  public void apply(@NotNull NavigableGameModel model) throws GameEventException {
    model.promoteVariation();
  }
}
