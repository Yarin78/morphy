package se.yarin.chess.timeline;

import lombok.NonNull;
import se.yarin.chess.NavigableGameModel;

/**
 * Event that deletes all the remaining moves from the current position.
 */
public class DeleteRemainingMovesEvent extends GameEvent {
    @Override
    public void apply(@NonNull NavigableGameModel model) throws GameEventException {
        model.deleteRemainingMoves();
    }
}
