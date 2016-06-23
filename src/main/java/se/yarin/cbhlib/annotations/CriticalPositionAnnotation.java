package se.yarin.cbhlib.annotations;

import lombok.AllArgsConstructor;
import lombok.Getter;
import se.yarin.chess.annotations.Annotation;

@AllArgsConstructor
public class CriticalPositionAnnotation extends Annotation {

    public enum CriticalPositionType {
        NONE,
        OPENING,
        MIDDLEGAME,
        ENDGAME
    }

    @Getter private CriticalPositionType type;
}
