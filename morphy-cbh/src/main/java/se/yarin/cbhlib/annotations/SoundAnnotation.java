package se.yarin.cbhlib.annotations;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.cbhlib.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

/**
 * Sound annotations are not supported.
 * ChessBase 13 doesn't either - probably a deprecated feature?
 */
@Deprecated
@EqualsAndHashCode(callSuper = false)
public class SoundAnnotation extends Annotation implements StatisticalAnnotation {
    @Getter
    private byte[] rawData;

    public SoundAnnotation(byte[] rawData) {
        this.rawData = rawData;
    }

    @Override
    public String toString() {
        return "SoundAnnotation = " + CBUtil.toHexString(rawData);
    }

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.flags.add(GameHeaderFlags.EMBEDDED_AUDIO);
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            buf.put(((SoundAnnotation) annotation).getRawData());
        }

        @Override
        public SoundAnnotation deserialize(ByteBuffer buf, int length) {
            byte data[] = new byte[length];
            buf.get(data);

            return new SoundAnnotation(data);
        }

        @Override
        public Class getAnnotationClass() {
            return SoundAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x10;
        }
    }
}
