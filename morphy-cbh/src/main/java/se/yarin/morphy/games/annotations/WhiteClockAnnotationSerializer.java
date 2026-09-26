package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.*;
import se.yarin.util.ByteBufferUtil;

/** Reads and writes a {@link WhiteClockAnnotation} in the v1 {@code .cba} format. */
public class WhiteClockAnnotationSerializer implements AnnotationSerializer {
  @Override
  public void serialize(ByteBuffer buf, Annotation annotation) {
    ByteBufferUtil.putIntB(buf, ((WhiteClockAnnotation) annotation).clockTime());
  }

  @Override
  public WhiteClockAnnotation deserialize(ByteBuffer buf, int length) {
    return ImmutableWhiteClockAnnotation.of(ByteBufferUtil.getIntB(buf));
  }

  @Override
  public Class getAnnotationClass() {
    return ImmutableWhiteClockAnnotation.class;
  }

  @Override
  public int getAnnotationType() {
    return 0x16;
  }
}
