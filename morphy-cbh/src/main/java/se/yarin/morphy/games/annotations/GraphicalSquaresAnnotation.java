package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import se.yarin.morphy.exceptions.MorphyAnnotationExecption;
import se.yarin.util.ByteBufferUtil;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.*;

@Value.Immutable
public abstract class GraphicalSquaresAnnotation extends Annotation implements StatisticalAnnotation {

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.noGraphicalSquares++;
        stats.flags.add(GameHeaderFlags.GRAPHICAL_SQUARES);
    }

    @Value.Immutable
    public interface Square {
        @Value.Parameter
        GraphicalAnnotationColor color();

        @Value.Parameter
        int sqi();
    }

    @Value.Parameter
    public abstract List<Square> squares();

    @Override
    public int priority() {
        return 6;
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            for (Square square : ((GraphicalSquaresAnnotation) annotation).squares()) {
                ByteBufferUtil.putByte(buf, square.color().getColorId());
                ByteBufferUtil.putByte(buf, square.sqi() + 1);
            }
        }

        @Override
        public GraphicalSquaresAnnotation deserialize(ByteBuffer buf, int length)
                throws MorphyAnnotationExecption {
            ArrayList<Square> squares = new ArrayList<>();
            for (int i = 0; i < length / 2; i++) {
                int color = ByteBufferUtil.getUnsignedByte(buf);
                int sqi = ByteBufferUtil.getUnsignedByte(buf) - 1;
                if (sqi < 0 || sqi > 63 || color < 0 || color > GraphicalAnnotationColor.maxColor())
                    throw new MorphyAnnotationExecption("Invalid graphical squares annotation");
                squares.add(ImmutableSquare.of(GraphicalAnnotationColor.fromInt(color), sqi));
            }
            return ImmutableGraphicalSquaresAnnotation.of(squares);
        }

        @Override
        public Class getAnnotationClass() {
            return ImmutableGraphicalSquaresAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x04;
        }

    }
}
