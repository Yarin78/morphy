package se.yarin.morphy.exceptions;

import se.yarin.chess.GameMovesModel;

/**
 * Exception thrown when failing to deserialize the moves of a ChessBase encoded game. The moves
 * parsed so far can be retrieve using {@link #getModel()}
 */
public class MorphyMoveDecodingException extends MorphyInvalidDataException {
  private GameMovesModel model = new GameMovesModel();

  public GameMovesModel getModel() {
    return model;
  }

  public void setModel(GameMovesModel model) {
    this.model = model;
  }

  public MorphyMoveDecodingException(String message) {
    super(message);
  }

  public MorphyMoveDecodingException(String message, Throwable cause, GameMovesModel model) {
    super(message, cause);
    this.model = model;
  }

  public MorphyMoveDecodingException(String message, Throwable cause) {
    super(message, cause);
  }
}
