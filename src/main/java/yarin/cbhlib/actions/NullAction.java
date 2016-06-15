package yarin.cbhlib.actions;

import yarin.chess.GameModel;

public class NullAction extends RecordedAction {
    private int commandType;

    public NullAction(int commandType) {
        this.commandType = commandType;
    }

    @Override
    public void apply(GameModel currentModel) {
    }

    @Override
    public String toString() {
        return "NullAction{" +
                "commandType=" + commandType +
                '}';
    }
}
