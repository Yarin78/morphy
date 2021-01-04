package se.yarin.cbhlib.moves;

import se.yarin.chess.GameMovesModel;

import java.nio.ByteBuffer;

public interface MoveEncoder {
    void encode(ByteBuffer buf, GameMovesModel movesModel);
    void decode(ByteBuffer buf, GameMovesModel movesModel) throws ChessBaseMoveDecodingException;
}
