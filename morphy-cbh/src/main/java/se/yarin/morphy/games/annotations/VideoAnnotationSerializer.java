package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.*;

/** Reads and writes a {@link VideoAnnotation} in the v1 {@code .cba} format. */
public class VideoAnnotationSerializer implements AnnotationSerializer {

  @Override
  public void serialize(ByteBuffer buf, Annotation annotation) {
    buf.put(((VideoAnnotation) annotation).rawData());
  }

  @Override
  public VideoAnnotation deserialize(ByteBuffer buf, int length) {
    // First byte seems to always be 1
    // Second byte is either 0x00, 0x2A or 0x35
    // Then follows a string (without any length specified)

    byte data[] = new byte[length];
    buf.get(data);

    return ImmutableVideoAnnotation.of(data);
  }

  @Override
  public Class getAnnotationClass() {
    return ImmutableVideoAnnotation.class;
  }

  @Override
  public int getAnnotationType() {
    return 0x20;
  }
}
