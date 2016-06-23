package se.yarin.chess.timeline;

import se.yarin.chess.NavigableGameModel;

/**
 * Exception thrown when trying to apply a {@link GameEvent} to a model.
 */
public class GameEventException extends Exception {
    private GameEvent event;
    private NavigableGameModel model;

    public GameEventException(GameEvent event, NavigableGameModel model, String message) {
        super(message);
        this.event = event;
        this.model = model;
    }
}
