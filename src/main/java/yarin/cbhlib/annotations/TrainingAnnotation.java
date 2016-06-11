package yarin.cbhlib.annotations;

import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class TrainingAnnotation extends Annotation {
    public TrainingAnnotation(ByteBuffer data) throws CBHFormatException {
        throw new CBHFormatException("Training annotations are not yet supported");
    }
}
