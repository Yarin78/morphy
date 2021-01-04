package se.yarin.morphy.gui;

import javafx.scene.control.Label;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Move;

public class MoveLabel extends Label {
    private Move move;
    private GameMovesModel.Node node;

    public MoveLabel(Move move, GameMovesModel.Node node) {
        this.move = move;
        this.node = node;
    }

    public Move getMove() {
        return move;
    }

    public GameMovesModel.Node getNode() {
        return node;
    }
}
