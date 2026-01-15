package se.yarin.chess.timeline;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.NavigableGameModel;

/**
 * Event that deletes all annotations in the entire game.
 */
public class DeleteAllAnnotationEvents extends GameEvent {
    @Override
    public void apply(@NotNull NavigableGameModel model) throws GameEventException {
        model.deleteAllAnnotations();
    }
}
