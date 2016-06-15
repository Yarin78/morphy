package yarin.cbhlib.actions;

import yarin.chess.GameModel;

public class DeleteVariationAction extends RecordedAction {
    @Override
    public void apply(GameModel currentModel) throws ApplyActionException {
        currentModel.getSelectedMove().deleteVariation();
    }
}
