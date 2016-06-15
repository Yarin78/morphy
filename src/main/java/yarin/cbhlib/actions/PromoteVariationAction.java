package yarin.cbhlib.actions;

import yarin.chess.GameModel;

public class PromoteVariationAction extends RecordedAction {
    @Override
    public void apply(GameModel currentModel) throws ApplyActionException {
        currentModel.getSelectedMove().promoteVariation();
    }
}
