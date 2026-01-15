package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import se.yarin.morphy.util.CBUtil;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

@Value.Immutable
public abstract class TrainingAnnotation extends Annotation implements StatisticalAnnotation {
  @Value.Parameter
  public abstract byte[] rawData();

  @Override
  public String toString() {
    return "TrainingAnnotation = " + CBUtil.toHexString(rawData());
  }

  @Override
  public void updateStatistics(AnnotationStatistics stats) {
    stats.noTraining++;
    stats.flags.add(GameHeaderFlags.TRAINING);
  }

  public static class Serializer implements AnnotationSerializer {
    @Override
    public void serialize(ByteBuffer buf, Annotation annotation) {
      buf.put(((TrainingAnnotation) annotation).rawData());
    }

    @Override
    public TrainingAnnotation deserialize(ByteBuffer buf, int length) {
      // TODO: Support this
      byte data[] = new byte[length];
      buf.get(data);

      return ImmutableTrainingAnnotation.of(data);
    }

    @Override
    public Class getAnnotationClass() {
      return ImmutableTrainingAnnotation.class;
    }

    @Override
    public int getAnnotationType() {
      return 0x09;
    }
  }
}
