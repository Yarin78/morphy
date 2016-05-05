package yarin.cbhlib.annotations;

import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class CriticalPositionAnnotation extends Annotation {
    public CriticalPositionAnnotation(GamePosition annotationPosition, ByteBuffer data) throws CBHFormatException {
        super(annotationPosition);
        throw new CBHFormatException("Critical positions are not yet supported");
    }
}


