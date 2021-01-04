package se.yarin.cbhlib.annotations;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import se.yarin.cbhlib.AnnotationSerializer;
import se.yarin.cbhlib.util.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

@EqualsAndHashCode(callSuper = false)
public class VariationColorAnnotation extends Annotation {
    @Getter
    private int red, green, blue;

    @Getter
    private boolean onlyMoves;

    @Getter
    private boolean onlyMainline;

    public VariationColorAnnotation(int red, int green, int blue, boolean onlyMoves, boolean onlyMainline) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.onlyMoves = onlyMoves;
        this.onlyMainline = onlyMainline;
    }

    @Override
    public String toString() {
        return String.format("Variation color = #%02X%02X%02X (%s, %s)",
                red, green, blue, onlyMoves ? "Moves only" : "Moves and annotations", onlyMainline ? "Mainline only" : "Include sublines");
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            VariationColorAnnotation vca = (VariationColorAnnotation) annotation;
            int flag = 0;
            if (vca.isOnlyMainline()) flag += 1;
            if (vca.isOnlyMoves()) flag += 2;
            ByteBufferUtil.putByte(buf, flag);
            ByteBufferUtil.putByte(buf, vca.getBlue());
            ByteBufferUtil.putByte(buf, vca.getGreen());
            ByteBufferUtil.putByte(buf, vca.getRed());
        }

        @Override
        public VariationColorAnnotation deserialize(ByteBuffer buf, int length) {
            int flag = buf.get();
            boolean onlyMainline = (flag & 1) == 1;
            boolean onlyMoves = (flag & 2) == 2;

            int b = ByteBufferUtil.getUnsignedByte(buf);
            int g = ByteBufferUtil.getUnsignedByte(buf);
            int r = ByteBufferUtil.getUnsignedByte(buf);

            return new VariationColorAnnotation(r, g, b, onlyMoves, onlyMainline);
        }

        @Override
        public Class getAnnotationClass() {
            return VariationColorAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x23;
        }
    }
}
