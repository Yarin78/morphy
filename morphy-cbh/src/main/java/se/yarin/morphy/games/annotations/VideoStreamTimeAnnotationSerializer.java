package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.*;
import se.yarin.util.ByteBufferUtil;

/** Reads and writes a {@link VideoStreamTimeAnnotation} in the v1 {@code .cba} format. */
public class VideoStreamTimeAnnotationSerializer implements AnnotationSerializer {
  @Override
  public void serialize(ByteBuffer buf, Annotation annotation) {
    ByteBufferUtil.putIntB(buf, ((VideoStreamTimeAnnotation) annotation).time());
  }

  @Override
  public VideoStreamTimeAnnotation deserialize(ByteBuffer buf, int length) {
    return ImmutableVideoStreamTimeAnnotation.of(ByteBufferUtil.getIntB(buf));
  }

  @Override
  public Class getAnnotationClass() {
    return ImmutableVideoStreamTimeAnnotation.class;
  }

  @Override
  public int getAnnotationType() {
    return 0x25;
  }
}
