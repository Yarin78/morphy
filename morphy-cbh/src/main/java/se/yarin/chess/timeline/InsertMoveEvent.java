package se.yarin.chess.timeline;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.IllegalMoveException;
import se.yarin.chess.NavigableGameModel;
import se.yarin.chess.ShortMove;

/**
 * Event that inserts a move at the current position, see {@link
 * se.yarin.chess.GameMovesModel.Node#insertMove(se.yarin.chess.Move)} )}
 */
public class InsertMoveEvent extends GameEvent {

  private ShortMove move;

  public InsertMoveEvent(ShortMove move) {
    this.move = move;
  }

  @Override
  public void apply(@NotNull NavigableGameModel model) throws GameEventException {
    try {
      model.insertMove(move.toMove(model.cursor().position()));
    } catch (IllegalMoveException e) {
      throw new GameEventException(this, model, "Illegal move: " + move.toString());
    }
  }
}
