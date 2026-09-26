package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.*;
import se.yarin.morphy.chessbase.annotations.CriticalPositionAnnotation.CriticalPositionType;

/** Reads and writes a {@link CriticalPositionAnnotation} in the v1 {@code .cba} format. */
public class CriticalPositionAnnotationSerializer implements AnnotationSerializer {
  @Override
  public void serialize(ByteBuffer buf, Annotation annotation) {
    buf.put((byte) ((CriticalPositionAnnotation) annotation).type().ordinal());
  }

  @Override
  public CriticalPositionAnnotation deserialize(ByteBuffer buf, int length) {
    return ImmutableCriticalPositionAnnotation.of(CriticalPositionType.values()[buf.get()]);
  }

  @Override
  public Class getAnnotationClass() {
    return ImmutableCriticalPositionAnnotation.class;
  }

  @Override
  public int getAnnotationType() {
    return 0x18;
  }
}
