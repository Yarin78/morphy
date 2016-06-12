package yarin.cbhlib.actions;

import yarin.chess.GameModel;
import yarin.chess.GamePosition;
import yarin.chess.Move;

public class AddMoveAction extends RecordedAction {

    private int fromSquare, toSquare;

    public AddMoveAction(int fromSquare, int toSquare, String code, String code2) {
        // TODO: Figure out code and code2
        this.fromSquare = fromSquare;
        this.toSquare = toSquare;
    }

    @Override
    public void apply(GameModel currentModel) {
        GamePosition selectedMove = currentModel.getSelectedMove();
        Move move = new Move(selectedMove.getPosition(), fromSquare / 8, fromSquare % 8, toSquare / 8, toSquare % 8);
        GamePosition position = selectedMove.addMove(move);
        currentModel.setSelectedMove(position);
    }
}
