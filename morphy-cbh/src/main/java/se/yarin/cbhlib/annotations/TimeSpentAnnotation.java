package se.yarin.cbhlib.annotations;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import se.yarin.util.ByteBufferUtil;
import se.yarin.cbhlib.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

@EqualsAndHashCode(callSuper = false)
public class TimeSpentAnnotation extends Annotation implements StatisticalAnnotation {
    @Getter
    private int hours;
    @Getter
    private int minutes;
    @Getter
    private int seconds;
    @Getter
    private int unknownByte;

    public TimeSpentAnnotation(int hours, int minutes, int seconds, int unknownByte) {
        this.hours = hours;
        this.minutes = minutes;
        this.seconds = seconds;
        this.unknownByte = unknownByte;
    }

    @Override
    public String toString() {
        String s;
        if (hours == 0 && minutes == 0) {
            s = Integer.toString(seconds);
        } else if (hours == 0) {
            s = String.format("%d:%02d", minutes, seconds);
        } else {
            s = String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return "Time spent = " + s;
    }

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.noTimeSpent++;
        stats.flags.add(GameHeaderFlags.TIME_SPENT);
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            TimeSpentAnnotation tsa = (TimeSpentAnnotation) annotation;
            ByteBufferUtil.putByte(buf, tsa.getHours());
            ByteBufferUtil.putByte(buf, tsa.getMinutes());
            ByteBufferUtil.putByte(buf, tsa.getSeconds());
            ByteBufferUtil.putByte(buf, tsa.getUnknownByte());
        }

        @Override
        public TimeSpentAnnotation deserialize(ByteBuffer buf, int length) {
            return new TimeSpentAnnotation(
                    ByteBufferUtil.getUnsignedByte(buf),
                    ByteBufferUtil.getUnsignedByte(buf),
                    ByteBufferUtil.getUnsignedByte(buf),
                    ByteBufferUtil.getUnsignedByte(buf));
        }

        @Override
        public Class getAnnotationClass() {
            return TimeSpentAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x07;
        }
    }
}
