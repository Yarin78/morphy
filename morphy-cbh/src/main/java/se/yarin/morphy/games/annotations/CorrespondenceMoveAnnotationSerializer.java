package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.*;

/** Reads and writes a {@link CorrespondenceMoveAnnotation} in the v1 {@code .cba} format. */
public class CorrespondenceMoveAnnotationSerializer implements AnnotationSerializer {
  @Override
  public void serialize(ByteBuffer buf, Annotation annotation) {
    buf.put(((CorrespondenceMoveAnnotation) annotation).rawData());
  }

  @Override
  public CorrespondenceMoveAnnotation deserialize(ByteBuffer buf, int length) {
    // TODO: Support this
    byte data[] = new byte[length];
    buf.get(data);

    return ImmutableCorrespondenceMoveAnnotation.of(data);
  }

  @Override
  public Class getAnnotationClass() {
    return ImmutableCorrespondenceMoveAnnotation.class;
  }

  @Override
  public int getAnnotationType() {
    return 0x19;
  }
}
