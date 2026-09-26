package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.*;

/** Reads and writes a {@link PictureAnnotation} in the v1 {@code .cba} format. */
public class PictureAnnotationSerializer implements AnnotationSerializer {
  @Override
  public void serialize(ByteBuffer buf, Annotation annotation) {
    buf.put(((PictureAnnotation) annotation).rawData());
  }

  @Override
  public PictureAnnotation deserialize(ByteBuffer buf, int length) {
    byte data[] = new byte[length];
    buf.get(data);

    return ImmutablePictureAnnotation.of(data);
  }

  @Override
  public Class getAnnotationClass() {
    return ImmutablePictureAnnotation.class;
  }

  @Override
  public int getAnnotationType() {
    return 0x11;
  }
}
