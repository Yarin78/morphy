package yarin.cbhlib.actions;

import yarin.chess.GameModel;
import yarin.chess.GamePosition;

public class DeleteRemainingMovesAction extends RecordedAction {
    @Override
    public void apply(GameModel currentModel) throws ApplyActionException {
        GamePosition pos = currentModel.getSelectedMove();
        pos.getForwardPositions().forEach(pos::deleteForwardPosition);
    }
}
