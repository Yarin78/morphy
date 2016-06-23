package se.yarin.chess.timeline;

import lombok.NonNull;
import se.yarin.chess.NavigableGameModel;

/**
 * Event that promotes the current variation.
 */
public class PromoteVariationEvent extends GameEvent {
    @Override
    public void apply(@NonNull NavigableGameModel model) throws GameEventException {
        model.promoteVariation();
    }
}
