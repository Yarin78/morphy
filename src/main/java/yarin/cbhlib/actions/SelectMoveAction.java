package yarin.cbhlib.actions;

import yarin.chess.GameModel;
import yarin.chess.GamePosition;

import java.util.ArrayList;
import java.util.List;

public class SelectMoveAction extends RecordedAction {
    private int selectedMoveNo; // According to DFS search in move graph?

    public SelectMoveAction(int selectedMoveNo) {
        this.selectedMoveNo = selectedMoveNo;
    }

    @Override
    public void apply(GameModel currentModel) {
        // TODO: Make this more efficient
        List<GamePosition> positions = new ArrayList<>();
        enumeratePositions(positions, currentModel.getGame());
        currentModel.setSelectedMove(positions.get(selectedMoveNo));
    }

    private void enumeratePositions(List<GamePosition> positions, GamePosition current) {
        positions.add(current);
        for (GamePosition position : current.getForwardPositions()) {
            enumeratePositions(positions, position);
        }
    }
}
