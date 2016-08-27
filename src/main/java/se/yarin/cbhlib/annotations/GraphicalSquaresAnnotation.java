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
public class GraphicalSquaresAnnotation extends Annotation implements StatisticalAnnotation {

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.noGraphicalSquares++;
        stats.flags.add(GameHeaderFlags.GRAPHICAL_SQUARES);
    }

    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Square {
        @Getter private GraphicalAnnotationColor color;
        @Getter private int sqi;

        @Override
        public String toString() {
            return "Square{" +
                    "color=" + color +
                    ", sqi=" + sqi +
                    '}';
        }
    }

    private Square[] squares;

    public Collection<Square> getSquares() {
        return Arrays.asList(squares);
    }

    public GraphicalSquaresAnnotation(@NonNull List<Square> squares) {
        this.squares = squares.toArray(new Square[0]);
    }

    @Override
    public int priority() {
        return 6;
    }

    @Override
    public String toString() {
        return "GraphicalSquaresAnnotation{" +
                "squares=" + squares +
                '}';
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            for (Square square : ((GraphicalSquaresAnnotation) annotation).getSquares()) {
                ByteBufferUtil.putByte(buf, square.getColor().getColorId());
                ByteBufferUtil.putByte(buf, square.getSqi() + 1);
            }
        }

        @Override
        public GraphicalSquaresAnnotation deserialize(ByteBuffer buf, int length)
                throws ChessBaseAnnotationException {
            ArrayList<Square> squares = new ArrayList<>();
            for (int i = 0; i < length / 2; i++) {
                int color = ByteBufferUtil.getUnsignedByte(buf);
                int sqi = ByteBufferUtil.getUnsignedByte(buf) - 1;
                if (sqi < 0 || sqi > 63 || color < 0 || color > GraphicalAnnotationColor.maxColor())
                    throw new ChessBaseAnnotationException("Invalid graphical squares annotation");
                squares.add(new Square(GraphicalAnnotationColor.fromInt(color), sqi));
            }
            return new GraphicalSquaresAnnotation(squares);
        }

        @Override
        public Class getAnnotationClass() {
            return GraphicalSquaresAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x04;
        }

    }
}
