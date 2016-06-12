package yarin.cbhlib.actions;

import yarin.chess.GameModel;

public abstract class RecordedAction {

    public boolean isFullUpdate() {
        return false;
    }

    public abstract void apply(GameModel currentModel);

}
