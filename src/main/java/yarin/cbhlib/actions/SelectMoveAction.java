package yarin.cbhlib.actions;

import yarin.chess.GameModel;

public class SelectMoveAction extends RecordedAction {
    private int selectedMoveNo; // According to DFS search in move graph?

    public SelectMoveAction(int selectedMoveNo) {
        this.selectedMoveNo = selectedMoveNo;
    }

    @Override
    public void apply(GameModel currentModel) {
        // TODO: Implement this
        // selectedMoveNo is probably the visited node number when doing a DFS search
    }
}
