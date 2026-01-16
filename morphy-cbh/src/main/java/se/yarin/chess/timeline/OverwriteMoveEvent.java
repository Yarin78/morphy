package se.yarin.chess.timeline;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.IllegalMoveException;
import se.yarin.chess.Move;
import se.yarin.chess.NavigableGameModel;
import se.yarin.chess.ShortMove;

/** Event that overwrites a move at the current position. */
public class OverwriteMoveEvent extends GameEvent {

  private ShortMove move;

  public OverwriteMoveEvent(@NotNull ShortMove move) {
    this.move = move;
  }

  @Override
  public void apply(@NotNull NavigableGameModel model) throws GameEventException {
    try {
      model.overwriteMove(move.toMove(model.cursor().position()));
    } catch (IllegalMoveException e) {
      throw new GameEventException(this, model, "Illegal move: " + move);
    }
  }
}
