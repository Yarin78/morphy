package se.yarin.chess.timeline;

import lombok.NonNull;
import se.yarin.chess.NavigableGameModel;

/**
 * An event that replaces all data in a model with the data from the new model.
 */
public class ReplaceAllEvent extends GameEvent {
    private NavigableGameModel newModel;

    public ReplaceAllEvent(NavigableGameModel newModel) {
        this.newModel = newModel;
    }

    @Override
    public void apply(@NonNull NavigableGameModel model) {
        model.replaceAll(newModel);
    }
}
