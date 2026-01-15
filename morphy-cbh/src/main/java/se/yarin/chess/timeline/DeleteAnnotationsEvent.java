package se.yarin.chess.timeline;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.NavigableGameModel;

/** Event that deletes the annotations at the current node. */
public class DeleteAnnotationsEvent extends GameEvent {
  @Override
  public void apply(@NotNull NavigableGameModel model) throws GameEventException {
    model.deleteAnnotations();
  }
}
