package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.Medal;
import se.yarin.morphy.chessbase.annotations.*;
import se.yarin.util.ByteBufferUtil;

/** Reads and writes a {@link MedalAnnotation} in the v1 {@code .cba} format. */
public class MedalAnnotationSerializer implements AnnotationSerializer {
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
