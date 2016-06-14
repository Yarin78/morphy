package yarin.cbhlib.actions;

import yarin.cbhlib.exceptions.CBMException;
import yarin.chess.GameModel;

/**
 * Thrown when @{@link RecordedAction#apply(GameModel)} was invalid
 */
public class ApplyActionException extends CBMException {

    private RecordedAction action;

    public RecordedAction getAction() {
        return action;
    }

    public ApplyActionException(RecordedAction action) {
        this.action = action;
    }

    public ApplyActionException(RecordedAction action, String message) {
        super(message);
        this.action = action;
    }
}
