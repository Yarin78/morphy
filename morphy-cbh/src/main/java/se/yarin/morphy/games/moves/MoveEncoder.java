package se.yarin.morphy.games.moves;

import se.yarin.chess.GameMovesModel;
import se.yarin.morphy.exceptions.MorphyMoveDecodingException;

import java.nio.ByteBuffer;

public interface MoveEncoder {
  void encode(ByteBuffer buf, GameMovesModel movesModel);

  void decode(ByteBuffer buf, GameMovesModel movesModel, boolean checkLegalMoves)
      throws MorphyMoveDecodingException;
}
