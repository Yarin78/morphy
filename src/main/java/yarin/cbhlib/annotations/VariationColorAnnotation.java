package yarin.cbhlib.annotations;

import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class VariationColorAnnotation extends Annotation {
    public VariationColorAnnotation(ByteBuffer data) throws CBHFormatException {
        throw new CBHFormatException("Variation color is not yet supported");
    }
}

