package se.yarin.cbhlib.media;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.NavigableGameModel;
import se.yarin.chess.timeline.GameEvent;
import se.yarin.chess.timeline.GameEventException;

/**
 * An unknown event in a ChessBase media file.
 * Nothing will happen with this event is applied to a game model.
 */
public class UnknownEvent extends GameEvent {

    private static final Logger log = LoggerFactory.getLogger(UnknownEvent.class);

    private int eventCode;

    public UnknownEvent(int eventCode) {
        this.eventCode = eventCode;
    }

    @Override
    public void apply(@NonNull NavigableGameModel model) throws GameEventException {
        log.debug("Unknown event with code " + eventCode + " was being applied");
    }
}
