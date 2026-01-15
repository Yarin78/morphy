package se.yarin.chess.timeline;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.NavigableGameModel;

/**
 * Event that deletes all the remaining moves from the current position.
 */
public class DeleteRemainingMovesEvent extends GameEvent {
    @Override
    public void apply(@NotNull NavigableGameModel model) throws GameEventException {
        model.deleteRemainingMoves();
    }
}
