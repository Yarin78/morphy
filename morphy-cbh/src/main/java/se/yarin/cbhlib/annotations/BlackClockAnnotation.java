package se.yarin.cbhlib.annotations;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import se.yarin.cbhlib.util.ByteBufferUtil;
import se.yarin.cbhlib.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

@EqualsAndHashCode(callSuper = false)
public class BlackClockAnnotation extends Annotation implements StatisticalAnnotation {
    @Getter
    private int clockTime;

    public BlackClockAnnotation(int clockTime) {
        this.clockTime = clockTime;
    }


    @Override
    public String toString() {
        int hours = clockTime / 100 / 3600;
        int minutes = (clockTime / 100 / 60) % 60;
        int seconds = (clockTime / 100) % 60;
        return String.format("BlackClock = %02d:%02d:%02d", hours, minutes, seconds);
    }

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.flags.add(GameHeaderFlags.BLACK_CLOCK);
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            ByteBufferUtil.putIntB(buf, ((BlackClockAnnotation) annotation).getClockTime());
        }

        @Override
        public BlackClockAnnotation deserialize(ByteBuffer buf, int length) {
            return new BlackClockAnnotation(ByteBufferUtil.getIntB(buf));
        }

        @Override
        public Class getAnnotationClass() {
            return BlackClockAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x17;
        }
    }
}
