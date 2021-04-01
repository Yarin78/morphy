package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.morphy.util.CBUtil;

import java.nio.ByteBuffer;

@Value.Immutable
public abstract class CorrespondenceMoveAnnotation extends Annotation implements StatisticalAnnotation {
    @Value.Parameter
    public abstract byte[] rawData();

    @Override
    public String toString() {
        return "CorrespondenceMoveAnnotation = " + CBUtil.toHexString(rawData());
    }

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.flags.add(GameHeaderFlags.CORRESPONDENCE_HEADER);
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            buf.put(((CorrespondenceMoveAnnotation) annotation).rawData());
        }

        @Override
        public CorrespondenceMoveAnnotation deserialize(ByteBuffer buf, int length) {
            // TODO: Support this
            byte data[] = new byte[length];
            buf.get(data);

            return ImmutableCorrespondenceMoveAnnotation.of(data);
        }

        @Override
        public Class getAnnotationClass() {
            return ImmutableCorrespondenceMoveAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x19;
        }
    }
}
