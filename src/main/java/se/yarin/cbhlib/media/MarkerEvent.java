package se.yarin.cbhlib.media;

import lombok.Getter;
import lombok.NonNull;
import se.yarin.chess.NavigableGameModel;
import se.yarin.chess.timeline.GameEvent;
import se.yarin.chess.timeline.GameEventException;

public class MarkerEvent extends GameEvent {
    @Getter
    private int id;

    public MarkerEvent(int id) {
        this.id = id;
    }

    @Override
    public void apply(@NonNull NavigableGameModel model) throws GameEventException {
    }

    @Override
    public String toString() {
        return super.toString() + ": " + id;
    }
}
