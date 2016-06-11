package yarin.cbhlib.annotations;

import yarin.cbhlib.Medals;
import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class MedalAnnotation extends Annotation {
    private Medals medals;

    public Medals getMedals() {
        return medals;
    }

    public MedalAnnotation(ByteBuffer data) throws CBHFormatException {
        if (data.get() != 0 || data.get() != 0)
            throw new CBHFormatException("Unexpected non-zero bytes in medal annotation");
        this.medals = Medals.decode(data.getShort());
    }
}
