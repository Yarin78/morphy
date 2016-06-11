package yarin.cbhlib.annotations;

import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class PawnStructureAnnotation extends Annotation {
    public PawnStructureAnnotation(ByteBuffer data) throws CBHFormatException {
        throw new CBHFormatException("Pawn structure is not yet supported");
    }
}

