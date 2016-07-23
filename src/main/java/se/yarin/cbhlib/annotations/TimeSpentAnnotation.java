package se.yarin.cbhlib.annotations;

import lombok.Getter;
import se.yarin.cbhlib.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

public class TimeSpentAnnotation extends Annotation {
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

    public static TimeSpentAnnotation deserialize(ByteBuffer buf) {
        return new TimeSpentAnnotation(
                ByteBufferUtil.getUnsignedByte(buf),
                ByteBufferUtil.getUnsignedByte(buf),
                ByteBufferUtil.getUnsignedByte(buf),
                ByteBufferUtil.getUnsignedByte(buf));
    }
}
