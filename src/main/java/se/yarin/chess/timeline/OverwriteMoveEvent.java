package se.yarin.chess.timeline;

import lombok.NonNull;
import se.yarin.chess.IllegalMoveException;
import se.yarin.chess.NavigableGameModel;
import se.yarin.chess.ShortMove;

/**
 * Event that overwrites a move at the current position.
 */
public class OverwriteMoveEvent extends GameEvent {

    private ShortMove move;

    public OverwriteMoveEvent(@NonNull ShortMove move) {
        this.move = move;
    }

    @Override
    public void apply(@NonNull NavigableGameModel model) throws GameEventException {
        try {
            model.overwriteMove(move);
        } catch (IllegalMoveException e) {
            throw new GameEventException(this, model, "Illegal move: " + move.toString());
        }
    }
}
