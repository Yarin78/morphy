package se.yarin.cbhlib.annotations;

import lombok.EqualsAndHashCode;
import se.yarin.cbhlib.AnnotationSerializer;
import se.yarin.cbhlib.ByteBufferUtil;
import se.yarin.cbhlib.CBUtil;
import se.yarin.cbhlib.Medal;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.EnumSet;

@EqualsAndHashCode(callSuper = false)
public class MedalAnnotation extends Annotation {
    private EnumSet<Medal> medals;

    public EnumSet<Medal> getMedals() {
        return medals.clone();
    }

    public MedalAnnotation(EnumSet<Medal> medals) {
        this.medals = medals;
    }

    public MedalAnnotation(Medal first, Medal... rest) {
        this(EnumSet.of(first, rest));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Medal medal : medals) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(medal);
        }

        return "Medals = " + sb.toString();
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            ByteBufferUtil.putIntB(buf, CBUtil.encodeMedals(((MedalAnnotation) annotation).getMedals()));
        }

        @Override
        public MedalAnnotation deserialize(ByteBuffer buf, int length) {
            return new MedalAnnotation(CBUtil.decodeMedals(ByteBufferUtil.getIntB(buf)));
        }

        @Override
        public Class getAnnotationClass() {
            return MedalAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x22;
        }
    }
}
