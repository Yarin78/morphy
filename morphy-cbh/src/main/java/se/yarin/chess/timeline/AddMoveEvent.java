package se.yarin.chess.timeline;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.IllegalMoveException;
import se.yarin.chess.NavigableGameModel;
import se.yarin.chess.ShortMove;

/**
 * Event that adds a new move at the current position.
 * If the position already has moves, a new variation is created.
 */
public class AddMoveEvent extends GameEvent {

    private ShortMove move;

    public AddMoveEvent(@NotNull ShortMove move) {
        this.move = move;
    }

    @Override
    public void apply(@NotNull NavigableGameModel model) throws GameEventException {
        try {
            model.addMove(move.toMove(model.cursor().position()));
        } catch (IllegalMoveException e) {
            throw new GameEventException(this, model, "Illegal move: " + move.toString());
        }
    }

    @Override
    public String toString() {
        return "AddMoveEvent{" +
                "move=" + move +
                '}';
    }
}
