package se.yarin.cbhlib.annotations;

import lombok.AllArgsConstructor;
import lombok.Getter;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

@AllArgsConstructor
public class CriticalPositionAnnotation extends Annotation {

    public enum CriticalPositionType {
        NONE,
        OPENING,
        MIDDLEGAME,
        ENDGAME
    }

    @Getter private CriticalPositionType type;

    public static CriticalPositionAnnotation deserialize(ByteBuffer buf) {
        return new CriticalPositionAnnotation(CriticalPositionType.values()[buf.get()]);
    }

}
