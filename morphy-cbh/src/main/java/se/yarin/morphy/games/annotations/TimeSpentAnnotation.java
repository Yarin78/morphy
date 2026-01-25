package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.util.ByteBufferUtil;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.regex.Pattern;

@Value.Immutable
public abstract class TimeSpentAnnotation extends Annotation implements StatisticalAnnotation {
  @Value.Parameter
  public abstract int hours();

  @Value.Parameter
  public abstract int minutes();

  @Value.Parameter
  public abstract int seconds();

  @Value.Parameter
  public abstract int unknownByte();

  @Override
  public String toString() {
    String s;
    if (hours() == 0 && minutes() == 0) {
      s = Integer.toString(seconds());
    } else if (hours() == 0) {
      s = String.format("%d:%02d", minutes(), seconds());
    } else {
      s = String.format("%d:%02d:%02d", hours(), minutes(), seconds());
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
      ByteBufferUtil.putByte(buf, tsa.hours());
      ByteBufferUtil.putByte(buf, tsa.minutes());
      ByteBufferUtil.putByte(buf, tsa.seconds());
      ByteBufferUtil.putByte(buf, tsa.unknownByte());
    }

    @Override
    public TimeSpentAnnotation deserialize(ByteBuffer buf, int length) {
      return ImmutableTimeSpentAnnotation.of(
          ByteBufferUtil.getUnsignedByte(buf),
          ByteBufferUtil.getUnsignedByte(buf),
          ByteBufferUtil.getUnsignedByte(buf),
          ByteBufferUtil.getUnsignedByte(buf));
    }

    @Override
    public Class getAnnotationClass() {
      return ImmutableTimeSpentAnnotation.class;
    }

    @Override
    public int getAnnotationType() {
      return 0x07;
    }
  }

  public static class PgnCodec implements AnnotationPgnCodec {
    private static final Logger log = LoggerFactory.getLogger(PgnCodec.class);
    private static final Pattern EMT_PATTERN = Pattern.compile("\\[%emt\\s+([^\\]]+)\\]");

    @Override
    @NotNull
    public Pattern getPattern() {
      return EMT_PATTERN;
    }

    @Override
    @Nullable
    public String encode(@NotNull Annotation annotation) {
      TimeSpentAnnotation a = (TimeSpentAnnotation) annotation;
      StringBuilder sb = new StringBuilder("[%emt ");
      sb.append(String.format("%d:%02d:%02d", a.hours(), a.minutes(), a.seconds()));
      if (a.unknownByte() != 0) {
        sb.append("|").append(a.unknownByte());
      }
      sb.append("]");
      return sb.toString();
    }

    @Override
    @Nullable
    public Annotation decode(@NotNull String data) {
      try {
        // Format: H:MM:SS or H:MM:SS|flag
        String[] mainParts = data.split("\\|");
        String timePart = mainParts[0];
        int unknownByte = mainParts.length > 1 ? Integer.parseInt(mainParts[1]) : 0;

        String[] timeParts = timePart.split(":");
        int hours = 0, minutes = 0, seconds = 0;

        if (timeParts.length == 3) {
          hours = Integer.parseInt(timeParts[0]);
          minutes = Integer.parseInt(timeParts[1]);
          seconds = Integer.parseInt(timeParts[2]);
        } else if (timeParts.length == 2) {
          minutes = Integer.parseInt(timeParts[0]);
          seconds = Integer.parseInt(timeParts[1]);
        } else if (timeParts.length == 1) {
          seconds = Integer.parseInt(timeParts[0]);
        }

        return ImmutableTimeSpentAnnotation.of(hours, minutes, seconds, unknownByte);
      } catch (Exception e) {
        log.warn("Failed to parse emt: {}", data);
        return null;
      }
    }

    @Override
    @NotNull
    public Class<? extends Annotation> getAnnotationClass() {
      return ImmutableTimeSpentAnnotation.class;
    }
  }
}
