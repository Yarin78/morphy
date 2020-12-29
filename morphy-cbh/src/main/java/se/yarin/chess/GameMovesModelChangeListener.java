package se.yarin.chess;

public interface GameMovesModelChangeListener {
    /**
     * A notification that a move model has changed
     * @param movesModel the move model that has changed
     * @param node a node in the model that is the root of the change.
     *             Everything above this node has not been changed, but everything
     *             below (may) have been.
     */
    void moveModelChanged(GameMovesModel movesModel, GameMovesModel.Node node);
}
