package se.yarin.chess.timeline;

import lombok.NonNull;
import se.yarin.chess.NavigableGameModel;

public class NullEvent extends GameEvent {
    @Override
    public void apply(@NonNull NavigableGameModel model) {
    }
}
