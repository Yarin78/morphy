package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import se.yarin.util.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Value.Immutable
public abstract class TimeControlAnnotation extends Annotation {

    @Value.Immutable
    public interface TimeSerie {
        @Value.Parameter
        int start();      // In hundredths of seconds
        @Value.Parameter
        int increment();  // In hundredths of seconds
        @Value.Parameter
        int moves();      // 1000 = rest of the game
        @Value.Parameter
        int type();       // ?? 0, 1 or 3. 3 only on last time serie, 1 usually means increment!?
    }

    @Value.Parameter
    public abstract List<TimeSerie> timeSeries();

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
        for (TimeSerie serie : timeSeries()) {
            if (sb.length() > 0) {
                sb.append("+");
            }
            if (serie.increment() > 0) {
                sb.append(String.format("(%s+%s)", hundredthsToString(serie.start()), hundredthsToString(serie.increment())));
            } else {
                sb.append(hundredthsToString(serie.start()));
            }
            if (serie.moves() > 0 && serie.moves() < 1000) {
                sb.append("/").append(serie.moves());
            }
        }
        return "TimeControl = " + sb.toString();
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            TimeControlAnnotation tca = (TimeControlAnnotation) annotation;
            for (int i = 0; i < 3; i++) {
                TimeSerie ts = i < tca.timeSeries().size() ? tca.timeSeries().get(i) : ImmutableTimeSerie.of(0, 0, 0, 0);
                ByteBufferUtil.putIntB(buf, ts.start());
                ByteBufferUtil.putIntB(buf, ts.increment());
                ByteBufferUtil.putShortB(buf, ts.moves());
                ByteBufferUtil.putByte(buf, ts.type());
            }
        }

        @Override
        public Annotation deserialize(ByteBuffer buf, int length) {
            ArrayList<TimeSerie> timeSeries = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                int start = ByteBufferUtil.getIntB(buf);
                int increment = ByteBufferUtil.getIntB(buf);
                int moves = ByteBufferUtil.getUnsignedShortB(buf);
                int type = ByteBufferUtil.getUnsignedByte(buf);
                timeSeries.add(ImmutableTimeSerie.of(start, increment, moves, type));
                if (moves == 1000) break;
            }

            return ImmutableTimeControlAnnotation.of(timeSeries);
        }

        @Override
        public Class getAnnotationClass() {
            return ImmutableTimeControlAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x24;
        }
    }

}
