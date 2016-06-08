package yarin.cbhlib.annotations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yarin.cbhlib.ByteBufferUtil;
import yarin.cbhlib.Medals;
import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class GraphicalSquaresAnnotation extends Annotation {
    private static final Logger log = LoggerFactory.getLogger(GraphicalSquaresAnnotation.class);

    public static class GraphicalSquare {
        private GraphicalColor color;
        private int square; // TODO: Square class

        public GraphicalSquare(GraphicalColor color, int square) {
            this.color = color;
            this.square = square;
        }

        public GraphicalColor getColor() {
            return color;
        }

        public int getSquare() {
            return square;
        }
    }

    private final List<GraphicalSquare> squares;

    public GraphicalSquaresAnnotation(GamePosition annotationPosition, ByteBuffer data, int length) throws CBHFormatException {
        super(annotationPosition);
        squares = new ArrayList<>();
        for (int i = 0; i < length / 2; i++) {
            int color = ByteBufferUtil.getUnsignedByte(data);
            int square = ByteBufferUtil.getUnsignedByte(data);
            squares.add(new GraphicalSquare(GraphicalColor.values()[color], square - 1));
        }
    }

    public List<GraphicalSquare> getSquares() {
        return squares;
    }
}
