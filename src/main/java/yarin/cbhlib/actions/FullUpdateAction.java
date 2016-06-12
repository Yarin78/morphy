package yarin.cbhlib.actions;

import yarin.chess.GameModel;

public class FullUpdateAction extends RecordedAction {
    private GameModel gameModel;

    public FullUpdateAction(GameModel gameModel) {
        this.gameModel = gameModel;
    }

    @Override
    public boolean isFullUpdate() {
        return true;
    }

    @Override
    public void apply(GameModel currentModel) {
        currentModel.setHeader(gameModel.getHeader());
        currentModel.setGame(gameModel.getGame(), gameModel.getSelectedMove());
    }
}
