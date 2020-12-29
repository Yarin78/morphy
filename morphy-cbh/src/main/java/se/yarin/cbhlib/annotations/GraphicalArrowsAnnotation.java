package se.yarin.cbhlib.annotations;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import se.yarin.cbhlib.AnnotationSerializer;
import se.yarin.cbhlib.ByteBufferUtil;
import se.yarin.cbhlib.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.*;

@EqualsAndHashCode(callSuper = false)
public class GraphicalArrowsAnnotation extends Annotation implements StatisticalAnnotation {

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.noGraphicalArrows++;
        stats.flags.add(GameHeaderFlags.GRAPHICAL_ARROWS);
    }

    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Arrow {
        @Getter private GraphicalAnnotationColor color;
        @Getter private int fromSqi, toSqi;
    }

    private Arrow[] arrows;

    public Collection<Arrow> getArrows() {
        return Arrays.asList(arrows);
    }

    public GraphicalArrowsAnnotation(@NonNull List<Arrow> arrows) {
        this.arrows = arrows.toArray(new Arrow[0]);
    }

    @Override
    public int priority() {
        return 5;
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            for (Arrow arrow : ((GraphicalArrowsAnnotation) annotation).getArrows()) {
                ByteBufferUtil.putByte(buf, arrow.getColor().getColorId());
                ByteBufferUtil.putByte(buf, arrow.getFromSqi() + 1);
                ByteBufferUtil.putByte(buf, arrow.getToSqi() + 1);
            }
        }

        @Override
        public GraphicalArrowsAnnotation deserialize(ByteBuffer buf, int length)
                throws ChessBaseAnnotationException {
            ArrayList<Arrow> arrows = new ArrayList<>();
            for (int i = 0; i < length / 3; i++) {
                int color = ByteBufferUtil.getUnsignedByte(buf);
                int fromSqi = ByteBufferUtil.getUnsignedByte(buf) - 1;
                int toSqi = ByteBufferUtil.getUnsignedByte(buf) - 1;
                if (fromSqi < 0 || fromSqi > 63 || toSqi < 0 || toSqi > 63
                        || color < 0 || color > GraphicalAnnotationColor.maxColor())
                    throw new ChessBaseAnnotationException("Invalid graphical arrows annotation");
                arrows.add(new Arrow(GraphicalAnnotationColor.fromInt(color), fromSqi, toSqi));
            }
            return new GraphicalArrowsAnnotation(arrows);
        }

        @Override
        public Class getAnnotationClass() {
            return GraphicalArrowsAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x05;
        }
    }
}
