package yarin.cbhlib.annotations;

import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class CorrespondenceHeaderAnnotation extends Annotation {
    public CorrespondenceHeaderAnnotation(ByteBuffer data) throws CBHFormatException {
        throw new CBHFormatException("Correspondence headers are not yet supported");
    }
}

