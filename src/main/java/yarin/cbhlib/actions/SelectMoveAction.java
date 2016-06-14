package yarin.cbhlib.actions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yarin.cbhlib.exceptions.CBMException;
import yarin.chess.GameModel;
import yarin.chess.GamePosition;

import java.util.ArrayList;
import java.util.List;

public class SelectMoveAction extends RecordedAction {
    private static final Logger log = LoggerFactory.getLogger(SelectMoveAction.class);

    private final int selectedMoveNo;

    public SelectMoveAction(int selectedMoveNo) {
        this.selectedMoveNo = selectedMoveNo;
    }

    @Override
    public void apply(GameModel currentModel) throws ApplyActionException {
        // TODO: Make this more efficient (just stop after selectedMoveNo is enough, should be few enough moves)
        // TODO: Traverse position primitive in game model?
        List<GamePosition> positions = new ArrayList<>();
        enumeratePositions(positions, currentModel.getGame());
        if (selectedMoveNo >= positions.size()) {
            throw new ApplyActionException(this, String.format("Tried to select move %d but there are only %d moves in the game", selectedMoveNo, positions.size()));
        } else {
            currentModel.setSelectedMove(positions.get(selectedMoveNo));

//            GamePosition selectedMove = currentModel.getSelectedMove();
//            log.info("Selected move is " + selectedMove.getPosition().toString() + " " + selectedMove.getMoveNumber() + " " + selectedMove.getPlayerToMove());
        }
    }

    private void enumeratePositions(List<GamePosition> positions, GamePosition current) {
        positions.add(current);
        for (GamePosition position : current.getForwardPositions()) {
            enumeratePositions(positions, position);
        }
    }

    @Override
    public String toString() {
        return "SelectMoveAction{" + selectedMoveNo + "}";
    }
}
