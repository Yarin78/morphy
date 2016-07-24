package se.yarin.cbhlib.annotations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

public class PawnStructureAnnotation extends Annotation {
    private static final Logger log = LoggerFactory.getLogger(PawnStructureAnnotation.class);

    private int type; // ??

    public PawnStructureAnnotation(int type) {
        if (type != 3) {
            log.warn("PawnStructure annotation of unknown type: " + type);
        }
        this.type = type;
    }

    @Override
    public String toString() {
        return "PawnStructureAnnotation";
    }

    public static PawnStructureAnnotation deserialize(ByteBuffer buf) {
        return new PawnStructureAnnotation(ByteBufferUtil.getUnsignedByte(buf));
    }
}
