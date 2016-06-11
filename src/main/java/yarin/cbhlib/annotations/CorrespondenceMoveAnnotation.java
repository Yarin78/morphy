package yarin.cbhlib.annotations;

import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class CorrespondenceMoveAnnotation extends Annotation {
    public CorrespondenceMoveAnnotation(ByteBuffer data) throws CBHFormatException {
        throw new CBHFormatException("Correspondence move are not yet supported");
    }
}

