package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.*;
import se.yarin.util.ByteBufferUtil;

/** Reads and writes a {@link BlackClockAnnotation} in the v1 {@code .cba} format. */
public class BlackClockAnnotationSerializer implements AnnotationSerializer {
  @Override
  public void serialize(ByteBuffer buf, Annotation annotation) {
    ByteBufferUtil.putIntB(buf, ((BlackClockAnnotation) annotation).clockTime());
  }

  @Override
  public BlackClockAnnotation deserialize(ByteBuffer buf, int length) {
    return ImmutableBlackClockAnnotation.of(ByteBufferUtil.getIntB(buf));
  }

  @Override
  public Class getAnnotationClass() {
    return ImmutableBlackClockAnnotation.class;
  }

  @Override
  public int getAnnotationType() {
    return 0x17;
  }
}
