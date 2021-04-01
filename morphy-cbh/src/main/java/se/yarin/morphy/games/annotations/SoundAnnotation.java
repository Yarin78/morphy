package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import se.yarin.morphy.util.CBUtil;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

/**
 * Sound annotations are not supported.
 * ChessBase 13 doesn't either - probably a deprecated feature?
 */
@Deprecated
@Value.Immutable
public abstract class SoundAnnotation extends Annotation implements StatisticalAnnotation {
    @Value.Parameter
    public abstract byte[] rawData();

    @Override
    public String toString() {
        return "SoundAnnotation = " + CBUtil.toHexString(rawData());
    }

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.flags.add(GameHeaderFlags.EMBEDDED_AUDIO);
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            buf.put(((SoundAnnotation) annotation).rawData());
        }

        @Override
        public SoundAnnotation deserialize(ByteBuffer buf, int length) {
            byte data[] = new byte[length];
            buf.get(data);

            return ImmutableSoundAnnotation.of(data);
        }

        @Override
        public Class getAnnotationClass() {
            return ImmutableSoundAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x10;
        }
    }
}
