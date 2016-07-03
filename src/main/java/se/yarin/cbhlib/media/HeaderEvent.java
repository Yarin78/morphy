package se.yarin.cbhlib.media;

import lombok.NonNull;
import se.yarin.chess.NavigableGameModel;
import se.yarin.chess.timeline.GameEvent;
import se.yarin.chess.timeline.GameEventException;

public class HeaderEvent extends GameEvent {
    @Override
    public void apply(@NonNull NavigableGameModel model) throws GameEventException {

    }
}
