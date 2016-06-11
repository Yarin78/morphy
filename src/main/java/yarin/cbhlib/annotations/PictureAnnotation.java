package yarin.cbhlib.annotations;

import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class PictureAnnotation extends Annotation {
    public PictureAnnotation(ByteBuffer data) throws CBHFormatException {
        throw new CBHFormatException("Picture is not yet supported");
    }
}

