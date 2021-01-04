package se.yarin.cbhlib.annotations;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.AnnotationSerializer;
import se.yarin.cbhlib.util.ByteBufferUtil;
import se.yarin.cbhlib.GameHeaderFlags;
import se.yarin.chess.Chess;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

@EqualsAndHashCode(callSuper = false)
public class PiecePathAnnotation extends Annotation implements StatisticalAnnotation {
    private static final Logger log = LoggerFactory.getLogger(PiecePathAnnotation.class);

    @Getter
    private int type; // ??

    @Getter
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

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.flags.add(GameHeaderFlags.PIECE_PATH);
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            PiecePathAnnotation ppa = (PiecePathAnnotation) annotation;
            ByteBufferUtil.putByte(buf, ppa.getType());
            ByteBufferUtil.putByte(buf, ppa.getSqi() + 1);
        }

        @Override
        public PiecePathAnnotation deserialize(ByteBuffer buf, int length) {
            return new PiecePathAnnotation(
                    ByteBufferUtil.getUnsignedByte(buf),
                    ByteBufferUtil.getUnsignedByte(buf) - 1);
        }

        @Override
        public Class getAnnotationClass() {
            return PiecePathAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x15;
        }
    }
}
