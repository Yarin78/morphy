package se.yarin.cbhlib.annotations;

import lombok.Getter;
import se.yarin.cbhlib.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

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

    public static VariationColorAnnotation deserialize(ByteBuffer buf) {
        int flag = buf.get();
        boolean onlyMainline = (flag & 1) == 1;
        boolean onlyMoves = (flag & 2) == 2;

        int b = ByteBufferUtil.getUnsignedByte(buf);
        int g = ByteBufferUtil.getUnsignedByte(buf);
        int r = ByteBufferUtil.getUnsignedByte(buf);

        return new VariationColorAnnotation(r, g, b, onlyMoves, onlyMainline);
    }

    @Override
    public String toString() {
        return String.format("Variation color = #%02X%02X%02X (%s, %s)",
                red, green, blue, onlyMoves ? "Moves only" : "Moves and annotations", onlyMainline ? "Mainline only" : "Include sublines");
    }
}
