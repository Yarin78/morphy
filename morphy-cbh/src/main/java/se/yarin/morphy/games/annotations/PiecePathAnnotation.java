package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.util.ByteBufferUtil;
import se.yarin.chess.Chess;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

@Value.Immutable
public abstract class PiecePathAnnotation extends Annotation implements StatisticalAnnotation {
    private static final Logger log = LoggerFactory.getLogger(PiecePathAnnotation.class);

    @Value.Parameter
    public abstract int type(); // ?? always 3?

    @Value.Parameter
    public abstract int sqi();

    @Override
    public String toString() {
        return "PiecePathAnnotation: " + Chess.sqiToStr(sqi());
    }

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.flags.add(GameHeaderFlags.PIECE_PATH);
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            PiecePathAnnotation ppa = (PiecePathAnnotation) annotation;
            ByteBufferUtil.putByte(buf, ppa.type());
            ByteBufferUtil.putByte(buf, ppa.sqi() + 1);
        }

        @Override
        public PiecePathAnnotation deserialize(ByteBuffer buf, int length) {
            return ImmutablePiecePathAnnotation.of(
                    ByteBufferUtil.getUnsignedByte(buf),
                    ByteBufferUtil.getUnsignedByte(buf) - 1);
        }

        @Override
        public Class getAnnotationClass() {
            return ImmutablePiecePathAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x15;
        }
    }
}
