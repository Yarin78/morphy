package yarin.cbhlib.actions;

import yarin.chess.GameModel;

public class UnknownAction extends RecordedAction {
    public UnknownAction(byte[] unknownBytes) {

    }

    @Override
    public void apply(GameModel currentModel) throws ApplyActionException {
        throw new ApplyActionException(this, "Tried to apply unknown action");
    }
}
