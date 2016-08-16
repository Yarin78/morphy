package se.yarin.cbhlib.annotations;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.AnnotationSerializer;
import se.yarin.cbhlib.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

@EqualsAndHashCode(callSuper = false)
public class PawnStructureAnnotation extends Annotation {
    private static final Logger log = LoggerFactory.getLogger(PawnStructureAnnotation.class);

    @Getter
    private int type; // ??

    public PawnStructureAnnotation(int type) {
        if (type != 3) {
            log.warn("PawnStructure annotation of unknown type: " + type);
        }
        this.type = type;
    }

    @Override
    public String toString() {
        return "PawnStructureAnnotation";
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            ByteBufferUtil.putByte(buf, ((PawnStructureAnnotation) annotation).getType());
        }

        @Override
        public PawnStructureAnnotation deserialize(ByteBuffer buf, int length) {
            return new PawnStructureAnnotation(ByteBufferUtil.getUnsignedByte(buf));
        }

        @Override
        public Class getAnnotationClass() {
            return PawnStructureAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x14;
        }
    }
}
