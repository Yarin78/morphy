package yarin.cbhlib.annotations;

import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class CriticalPositionAnnotation extends Annotation {

    public enum CriticalPositionType {
        NONE,
        OPENING,
        MIDDLEGAME,
        ENDGAME
    }

    private CriticalPositionType type;

    public CriticalPositionType getType() {
        return type;
    }

    public CriticalPositionAnnotation(ByteBuffer data) throws CBHFormatException {
        type = CriticalPositionType.values()[data.get()];
    }
}
