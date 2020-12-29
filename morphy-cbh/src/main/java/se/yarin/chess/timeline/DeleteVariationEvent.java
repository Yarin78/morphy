package se.yarin.chess.timeline;

import lombok.NonNull;
import se.yarin.chess.NavigableGameModel;

/**
 * Event that deletes the current variation.
 * The cursor will be update to the position before the variation started.
 */
public class DeleteVariationEvent extends GameEvent {
    @Override
    public void apply(@NonNull NavigableGameModel model) throws GameEventException {
        model.deleteVariation();
    }
}
