package se.yarin.morphy.games.moves;

import se.yarin.chess.GameMovesModel;
import se.yarin.chess.ShortMove;
import se.yarin.morphy.exceptions.MorphyMoveDecodingException;

import java.nio.ByteBuffer;

public class GameQuotationMoveEncoder implements MoveEncoder {

  private final MoveEncoder encoder;

  public GameQuotationMoveEncoder() {
    encoder = new SimpleMoveEncoder(8, true, false);
  }

  @Override
  public void encode(ByteBuffer buf, GameMovesModel movesModel) {
    // A game quotation must have it's comments and annotations stripped
    GameMovesModel simplifiedGame = new GameMovesModel(movesModel);
    simplifiedGame.deleteAllAnnotations();
    simplifiedGame.deleteAllVariations();

    // ChessBase also expects an extra null move to be written for some reason
    GameMovesModel.Node current = simplifiedGame.root();
    while (current.hasMoves()) {
      current = current.mainNode();
    }
    current.addMove(ShortMove.nullMove());
    encoder.encode(buf, simplifiedGame);
  }

  @Override
  public void decode(ByteBuffer buf, GameMovesModel movesModel, boolean checkLegalMoves)
      throws MorphyMoveDecodingException {
    encoder.decode(buf, movesModel, checkLegalMoves);

    // Get rid of the trailing null move
    GameMovesModel.Node current = movesModel.root();
    while (current.hasMoves()) {
      current = current.mainNode();
    }
    if (current.lastMove().isNullMove()) {
      current.deleteNode();
    }
  }
}
