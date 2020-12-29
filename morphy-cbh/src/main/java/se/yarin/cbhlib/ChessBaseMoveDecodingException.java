package se.yarin.cbhlib;

import se.yarin.chess.GameMovesModel;

/**
 * Exception thrown when failing to deserialize the moves of a ChessBase encoded game.
 * The moves parsed so far can be retrieve using {@link #getModel()}
 */
public class ChessBaseMoveDecodingException extends ChessBaseInvalidDataException {
    private GameMovesModel model = new GameMovesModel();

    public GameMovesModel getModel() {
        return model;
    }

    public void setModel(GameMovesModel model) {
        this.model = model;
    }

    public ChessBaseMoveDecodingException(String message) {
        super(message);
    }

    public ChessBaseMoveDecodingException(String message, Throwable cause, GameMovesModel model) {
        super(message, cause);
        this.model = model;
    }

    public ChessBaseMoveDecodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
