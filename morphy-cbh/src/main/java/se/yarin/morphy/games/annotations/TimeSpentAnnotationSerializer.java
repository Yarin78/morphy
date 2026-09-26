package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.*;
import se.yarin.util.ByteBufferUtil;

/** Reads and writes a {@link TimeSpentAnnotation} in the v1 {@code .cba} format. */
public class TimeSpentAnnotationSerializer implements AnnotationSerializer {
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
