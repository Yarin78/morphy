package yarin.cbhlib.annotations;

import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class PiecePathAnnotation extends Annotation {
    public PiecePathAnnotation(ByteBuffer data) throws CBHFormatException {
        throw new CBHFormatException("yarin.chess.Piece path is not yet supported");
    }
}


