package se.yarin.cbhlib.annotations;

import lombok.AllArgsConstructor;
import se.yarin.cbhlib.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class TimeControlAnnotation extends Annotation {
    @AllArgsConstructor
    private static class TimeSerie {
        private int start;      // In hundredths of seconds
        private int increment;  // In hundredths of seconds
        private int moves;      // 1000 = rest of the game
        private int type;       // ??
    }

    private TimeSerie[] timeSeries;

    private String hundredthsToString(int hundredths) {
        int seconds = hundredths / 100;
        int minutes = seconds / 60;
        seconds %= 60;
        if (minutes > 0 && seconds > 0) {
            return String.format("%dm%ds", minutes, seconds);
        }
        if (minutes > 0) {
            return String.format("%dm", minutes);
        }
        return String.format("%ds", seconds);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (TimeSerie serie : timeSeries) {
            if (sb.length() > 0) {
                sb.append("+");
            }
            if (serie.increment > 0) {
                sb.append(String.format("(%s+%s)", hundredthsToString(serie.start), hundredthsToString(serie.increment)));
            } else {
                sb.append(hundredthsToString(serie.start));
            }
            if (serie.moves > 0 && serie.moves < 1000) {
                sb.append("/").append(serie.moves);
            }
        }
        return "TimeControl = " + sb.toString();
    }

    public TimeControlAnnotation(TimeSerie[] timeSeries) {
        this.timeSeries = timeSeries.clone();
    }

    public static Annotation deserialize(ByteBuffer buf) {
        ArrayList<TimeSerie> timeSeries = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int start = ByteBufferUtil.getIntB(buf);
            int increment = ByteBufferUtil.getIntB(buf);
            int moves = ByteBufferUtil.getUnsignedShortB(buf);
            int type = ByteBufferUtil.getUnsignedByte(buf);
            timeSeries.add(new TimeSerie(start, increment, moves, type));
            if (moves == 1000) break;
        }

        return new TimeControlAnnotation(timeSeries.toArray(new TimeSerie[0]));
    }
}
