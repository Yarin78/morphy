package se.yarin.cbhlib.annotations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.ByteBufferUtil;
import se.yarin.chess.Chess;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

public class PiecePathAnnotation extends Annotation {
    private static final Logger log = LoggerFactory.getLogger(PiecePathAnnotation.class);

    private int type; // ??
    private int sqi;

    public PiecePathAnnotation(int type, int sqi) {
        if (type != 3) {
            log.warn("PiecePath annotation of unknown type: " + type);
        }
        this.type = type;
        this.sqi = sqi;
    }

    @Override
    public String toString() {
        return "PiecePathAnnotation: " + Chess.sqiToStr(sqi);
    }

    public static PiecePathAnnotation deserialize(ByteBuffer buf) {
        return new PiecePathAnnotation(
                ByteBufferUtil.getUnsignedByte(buf),
                ByteBufferUtil.getUnsignedByte(buf) - 1);
    }
}
