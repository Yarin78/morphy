package se.yarin.chess.timeline;

import lombok.NonNull;
import se.yarin.chess.NavigableGameModel;

/**
 * Event that deletes the annotations at the current node.
 */
public class DeleteAnnotationsEvent extends GameEvent {
    @Override
    public void apply(@NonNull NavigableGameModel model) throws GameEventException {
        model.deleteAnnotations();
    }
}
