package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import se.yarin.morphy.games.Medal;
import se.yarin.util.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.EnumSet;

@Value.Immutable
public abstract class MedalAnnotation extends Annotation implements StatisticalAnnotation {

  @Value.Parameter
  public abstract EnumSet<Medal> medals();

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (Medal medal : medals()) {
      if (sb.length() > 0) sb.append(", ");
      sb.append(medal);
    }

    return "Medals = " + sb.toString();
  }

  @Override
  public void updateStatistics(AnnotationStatistics stats) {
    stats.medals.addAll(medals());
  }

  public static class Serializer implements AnnotationSerializer {
    @Override
    public void serialize(ByteBuffer buf, Annotation annotation) {
      ByteBufferUtil.putIntB(buf, Medal.encode(((MedalAnnotation) annotation).medals()));
    }

    @Override
    public MedalAnnotation deserialize(ByteBuffer buf, int length) {
      return ImmutableMedalAnnotation.of(Medal.decode(ByteBufferUtil.getIntB(buf)));
    }

    @Override
    public Class getAnnotationClass() {
      return ImmutableMedalAnnotation.class;
    }

    @Override
    public int getAnnotationType() {
      return 0x22;
    }
  }
}
