package se.yarin.chess.timeline;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.NavigableGameModel;

public class NullEvent extends GameEvent {
    @Override
    public void apply(@NotNull NavigableGameModel model) {
    }
}
