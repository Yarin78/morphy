package se.yarin.cbhlib.annotations;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import se.yarin.cbhlib.AnnotationSerializer;
import se.yarin.cbhlib.util.CBUtil;
import se.yarin.cbhlib.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

@EqualsAndHashCode(callSuper = false)
public class TrainingAnnotation extends Annotation implements StatisticalAnnotation {
    @Getter
    private byte[] rawData;

    public TrainingAnnotation(byte[] rawData) {
        this.rawData = rawData;
    }

    @Override
    public String toString() {
        return "TrainingAnnotation = " + CBUtil.toHexString(rawData);
    }

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.noTraining++;
        stats.flags.add(GameHeaderFlags.TRAINING);
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            buf.put(((TrainingAnnotation) annotation).getRawData());
        }

        @Override
        public TrainingAnnotation deserialize(ByteBuffer buf, int length) {
            // TODO: Support this
            byte data[] = new byte[length];
            buf.get(data);

            return new TrainingAnnotation(data);
        }

        @Override
        public Class getAnnotationClass() {
            return TrainingAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x09;
        }
    }
}
