package yarin.cbhlib.annotations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yarin.cbhlib.ByteBufferUtil;
import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class GraphicalArrowsAnnotation extends Annotation {
    private static final Logger log = LoggerFactory.getLogger(GraphicalArrowsAnnotation.class);

    public static class GraphicalArrow {
        private GraphicalColor color;
        private int fromSquare, toSquare; // TODO: Square class

        public GraphicalArrow(GraphicalColor color, int fromSquare, int toSquare) {
            this.color = color;
            this.fromSquare = fromSquare;
            this.toSquare = toSquare;
        }

        public GraphicalColor getColor() {
            return color;
        }

        public int getFromSquare() {
            return fromSquare;
        }

        public int getToSquare() {
            return toSquare;
        }
    }

    private List<GraphicalArrow> arrows;

    public GraphicalArrowsAnnotation(ByteBuffer data, int length) throws CBHFormatException {
        arrows = new ArrayList<>();
        for (int i = 0; i < length / 3; i++) {
            int color = ByteBufferUtil.getUnsignedByte(data);
            int fromSquare = ByteBufferUtil.getUnsignedByte(data);
            int toSquare = ByteBufferUtil.getUnsignedByte(data);
            arrows.add(new GraphicalArrow(GraphicalColor.values()[color], fromSquare - 1, toSquare - 1));
            log.debug(color + " " + fromSquare + " " + toSquare);
        }
    }

    public List<GraphicalArrow> getArrows() {
        return arrows;
    }
}
