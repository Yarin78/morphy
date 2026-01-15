package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import se.yarin.util.ByteBufferUtil;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

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
}
