package se.yarin.cbhlib.annotations;

import lombok.Getter;
import se.yarin.cbhlib.AnnotationSerializer;
import se.yarin.cbhlib.CBUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

public class CorrespondenceMoveAnnotation extends Annotation {
    @Getter
    private byte[] rawData;

    public CorrespondenceMoveAnnotation(byte[] rawData) {
        this.rawData = rawData;
    }

    @Override
    public String toString() {
        return "CorrespondenceMoveAnnotation = " + CBUtil.toHexString(rawData);
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            buf.put(((CorrespondenceMoveAnnotation) annotation).getRawData());
        }

        @Override
        public CorrespondenceMoveAnnotation deserialize(ByteBuffer buf, int length) {
            // TODO: Support this
            byte data[] = new byte[length];
            buf.get(data);

            return new CorrespondenceMoveAnnotation(data);
        }

        @Override
        public Class getAnnotationClass() {
            return CorrespondenceMoveAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x19;
        }
    }
}
