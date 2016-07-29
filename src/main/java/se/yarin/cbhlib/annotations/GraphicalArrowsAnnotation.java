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

public class GraphicalArrowsAnnotation extends Annotation {

    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Arrow {
        @Getter private GraphicalAnnotationColor color;
        @Getter private int fromSqi, toSqi;
    }

    @Getter private Collection<Arrow> arrows;

    public GraphicalArrowsAnnotation(@NonNull List<Arrow> arrows) {
        this.arrows = Collections.unmodifiableCollection(arrows);
    }

    @Override
    public int priority() {
        return 5;
    }

    public static GraphicalArrowsAnnotation deserialize(ByteBuffer buf, int length)
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

}
