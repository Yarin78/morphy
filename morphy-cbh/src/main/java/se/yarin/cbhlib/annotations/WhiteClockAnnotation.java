package se.yarin.cbhlib.annotations;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import se.yarin.util.ByteBufferUtil;
import se.yarin.cbhlib.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

@EqualsAndHashCode(callSuper = false)
public class WhiteClockAnnotation extends Annotation implements StatisticalAnnotation {
    @Getter
    private int clockTime;

    public WhiteClockAnnotation(int clockTime) {
        this.clockTime = clockTime;
    }

    @Override
    public String toString() {
        int hours = clockTime / 100 / 3600;
        int minutes = (clockTime / 100 / 60) % 60;
        int seconds = (clockTime / 100) % 60;
        return String.format("WhiteClock = %02d:%02d:%02d", hours, minutes, seconds);
    }

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.flags.add(GameHeaderFlags.WHITE_CLOCK);
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            ByteBufferUtil.putIntB(buf, ((WhiteClockAnnotation) annotation).getClockTime());
        }

        @Override
        public WhiteClockAnnotation deserialize(ByteBuffer buf, int length) {
            return new WhiteClockAnnotation(ByteBufferUtil.getIntB(buf));
        }

        @Override
        public Class getAnnotationClass() {
            return WhiteClockAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x16;
        }
    }
}
