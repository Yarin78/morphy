package se.yarin.chess.timeline;

import lombok.NonNull;
import se.yarin.chess.NavigableGameModel;

/**
 * An event that updates a {@link se.yarin.chess.NavigableGameModel}.
 * Events should be immutable.
 */
public abstract class GameEvent {

    /**
     * Applies the event on a {@link NavigableGameModel}.
     * @param model the model to apply the event to
     * @throws GameEventException if the event could not be applied
     */
    public abstract void apply(@NonNull NavigableGameModel model) throws GameEventException;

    /**
     * @return true if this event incrementally updates the game model;
     * false if the entire game model is changed
     */
    boolean isIncremental() {
        return true;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }
}
