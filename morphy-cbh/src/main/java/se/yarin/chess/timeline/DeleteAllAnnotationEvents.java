package se.yarin.chess.timeline;

import lombok.NonNull;
import se.yarin.chess.NavigableGameModel;

/**
 * Event that deletes all annotations in the entire game.
 */
public class DeleteAllAnnotationEvents extends GameEvent {
    @Override
    public void apply(@NonNull NavigableGameModel model) throws GameEventException {
        model.deleteAllAnnotations();
    }
}
