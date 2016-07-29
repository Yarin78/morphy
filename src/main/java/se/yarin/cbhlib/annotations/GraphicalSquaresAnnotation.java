package se.yarin.cbhlib.annotations;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import se.yarin.cbhlib.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class GraphicalSquaresAnnotation extends Annotation {

    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Square {
        @Getter private GraphicalAnnotationColor color;
        @Getter private int sqi;
    }

    @Getter private Collection<Square> squares;

    public GraphicalSquaresAnnotation(@NonNull List<Square> squares) {
        this.squares = Collections.unmodifiableCollection(squares);
    }

    @Override
    public int priority() {
        return 6;
    }

    public static GraphicalSquaresAnnotation deserialize(ByteBuffer buf, int length)
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
}
