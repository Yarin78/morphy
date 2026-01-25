package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.util.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Value.Immutable
public abstract class TimeControlAnnotation extends Annotation {

  @Value.Immutable
  public interface TimeSerie {
    @Value.Parameter
    int start(); // In hundredths of seconds

    @Value.Parameter
    int increment(); // In hundredths of seconds

    @Value.Parameter
    int moves(); // 1000 = rest of the game

    @Value.Parameter
    int type(); // ?? 0, 1 or 3. 3 only on last time serie, 1 usually means increment!?
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
        sb.append(
            String.format(
                "(%s+%s)",
                hundredthsToString(serie.start()), hundredthsToString(serie.increment())));
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
        TimeSerie ts =
            i < tca.timeSeries().size()
                ? tca.timeSeries().get(i)
                : ImmutableTimeSerie.of(0, 0, 0, 0);
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

  public static class PgnCodec implements AnnotationPgnCodec {
    private static final Logger log = LoggerFactory.getLogger(PgnCodec.class);
    private static final Pattern TC_PATTERN = Pattern.compile("\\[%tc\\s+([^\\]]+)\\]");

    @Override
    @NotNull
    public Pattern getPattern() {
      return TC_PATTERN;
    }

    @Override
    @Nullable
    public String encode(@NotNull Annotation annotation) {
      TimeControlAnnotation a = (TimeControlAnnotation) annotation;
      StringBuilder sb = new StringBuilder("[%tc ");
      boolean first = true;
      for (TimeSerie ts : a.timeSeries()) {
        if (!first) sb.append("+");
        first = false;

        if (ts.increment() > 0) {
          sb.append("(").append(AnnotationPgnUtil.formatTimeControlDuration(ts.start()));
          sb.append("+").append(AnnotationPgnUtil.formatTimeControlDuration(ts.increment())).append(")");
        } else {
          sb.append(AnnotationPgnUtil.formatTimeControlDuration(ts.start()));
        }
        if (ts.moves() > 0 && ts.moves() < 1000) {
          sb.append("/").append(ts.moves());
        }
      }
      sb.append("]");
      return sb.toString();
    }

    @Override
    @Nullable
    public Annotation decode(@NotNull String data) {
      try {
        // Format: period1+period2+period3 where each period is time/moves or (time+inc)/moves
        List<TimeSerie> series = new ArrayList<>();
        String[] periodParts = data.split("\\+(?![^(]*\\))"); // Split on + not inside parentheses

        for (String period : periodParts) {
          period = period.trim();
          int start = 0;
          int increment = 0;
          int moves = 1000; // Default: rest of game
          int type = 0;

          // Check for /moves suffix
          int slashIdx = period.lastIndexOf('/');
          if (slashIdx > 0) {
            try {
              moves = Integer.parseInt(period.substring(slashIdx + 1));
              period = period.substring(0, slashIdx);
            } catch (NumberFormatException e) {
              // Not a valid moves count, leave as is
            }
          }

          // Check for (time+inc) format
          if (period.startsWith("(") && period.endsWith(")")) {
            period = period.substring(1, period.length() - 1);
            String[] incParts = period.split("\\+");
            start = AnnotationPgnUtil.parseTimeControlDuration(incParts[0].trim());
            if (incParts.length > 1) {
              increment = AnnotationPgnUtil.parseTimeControlDuration(incParts[1].trim());
            }
          } else {
            start = AnnotationPgnUtil.parseTimeControlDuration(period);
          }

          series.add(ImmutableTimeSerie.of(start, increment, moves, type));
        }

        return ImmutableTimeControlAnnotation.of(series);
      } catch (Exception e) {
        log.warn("Failed to parse tc: {}", data);
        return null;
      }
    }

    @Override
    @NotNull
    public Class<? extends Annotation> getAnnotationClass() {
      return ImmutableTimeControlAnnotation.class;
    }
  }
}
