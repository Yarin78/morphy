package se.yarin.morphy.games.annotations;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import se.yarin.cbhlib.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class CriticalPositionAnnotation extends Annotation implements StatisticalAnnotation {

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.flags.add(GameHeaderFlags.CRITICAL_POSITION);
    }

    public enum CriticalPositionType {
        NONE,
        OPENING,
        MIDDLEGAME,
        ENDGAME
    }

    @Getter
    private CriticalPositionType type;

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            buf.put((byte) ((CriticalPositionAnnotation) annotation).getType().ordinal());
        }

        @Override
        public CriticalPositionAnnotation deserialize(ByteBuffer buf, int length) {
            return new CriticalPositionAnnotation(CriticalPositionType.values()[buf.get()]);
        }

        @Override
        public Class getAnnotationClass() {
            return CriticalPositionAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x18;
        }
    }
}
