package se.yarin.chess.annotations;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.GameMovesModel;

/**
 * An annotation of a chess move or position in the {@link GameMovesModel}. An annotation is always
 * attached to a {@link se.yarin.chess.GameMovesModel.Node}, and then refers to either the position
 * at that node, or the move leading up to that node (the {@link GameMovesModel.Node#lastMove()}.
 *
 * <p>An Annotation is immutable.
 */
public abstract class Annotation {

  @Override
  public String toString() {
    return this.getClass().getSimpleName();
  }
}
