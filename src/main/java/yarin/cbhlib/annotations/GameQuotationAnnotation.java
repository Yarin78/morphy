package yarin.cbhlib.annotations;

import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class GameQuotationAnnotation extends Annotation {
    public GameQuotationAnnotation(ByteBuffer data) throws CBHFormatException {
        throw new CBHFormatException("Game quotations are not yet supported");
    }
}

