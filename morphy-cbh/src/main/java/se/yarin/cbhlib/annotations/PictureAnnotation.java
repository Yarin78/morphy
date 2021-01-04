package se.yarin.cbhlib.annotations;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.cbhlib.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

/**
 * Picture annotations are not supported.
 * ChessBase 13 doesn't either - probably a deprecated feature?
 */
@Deprecated
@EqualsAndHashCode(callSuper = false)
public class PictureAnnotation extends Annotation implements StatisticalAnnotation {
    @Getter
    private byte[] rawData;

    public PictureAnnotation(byte[] rawData) {
        this.rawData = rawData;
    }

    @Override
    public String toString() {
        return "PictureAnnotation = " + CBUtil.toHexString(rawData);
    }

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.flags.add(GameHeaderFlags.EMBEDDED_PICTURE);
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            buf.put(((PictureAnnotation) annotation).getRawData());
        }

        @Override
        public PictureAnnotation deserialize(ByteBuffer buf, int length) {
            byte data[] = new byte[length];
            buf.get(data);

            return new PictureAnnotation(data);
        }

        @Override
        public Class getAnnotationClass() {
            return PictureAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x11;
        }
    }
}
