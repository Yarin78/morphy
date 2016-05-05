package yarin.cbhlib.annotations;

import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class PawnStructureAnnotation extends Annotation {
    public PawnStructureAnnotation(GamePosition annotationPosition, ByteBuffer data) throws CBHFormatException {
        super(annotationPosition);
        throw new CBHFormatException("Pawn structure is not yet supported");
    }
}

