package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

@Value.Immutable
public abstract class CriticalPositionAnnotation extends Annotation implements StatisticalAnnotation {

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

    @Value.Parameter
    public abstract CriticalPositionType type();

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            buf.put((byte) ((CriticalPositionAnnotation) annotation).type().ordinal());
        }

        @Override
        public CriticalPositionAnnotation deserialize(ByteBuffer buf, int length) {
            return ImmutableCriticalPositionAnnotation.of(CriticalPositionType.values()[buf.get()]);
        }

        @Override
        public Class getAnnotationClass() {
            return ImmutableCriticalPositionAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x18;
        }
    }
}
