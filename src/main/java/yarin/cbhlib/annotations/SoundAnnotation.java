package yarin.cbhlib.annotations;

import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class SoundAnnotation extends Annotation {
    public SoundAnnotation(ByteBuffer data) throws CBHFormatException {
        throw new CBHFormatException("Sound is not yet supported");
    }
}

