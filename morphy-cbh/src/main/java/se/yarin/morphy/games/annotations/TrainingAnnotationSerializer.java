package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.*;

/** Reads and writes a {@link TrainingAnnotation} in the v1 {@code .cba} format. */
public class TrainingAnnotationSerializer implements AnnotationSerializer {
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
