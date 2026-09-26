package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.*;

/** Reads and writes a {@link SoundAnnotation} in the v1 {@code .cba} format. */
public class SoundAnnotationSerializer implements AnnotationSerializer {
  @Override
  public void serialize(ByteBuffer buf, Annotation annotation) {
    buf.put(((SoundAnnotation) annotation).rawData());
  }

  @Override
  public SoundAnnotation deserialize(ByteBuffer buf, int length) {
    byte data[] = new byte[length];
    buf.get(data);

    return ImmutableSoundAnnotation.of(data);
  }

  @Override
  public Class getAnnotationClass() {
    return ImmutableSoundAnnotation.class;
  }

  @Override
  public int getAnnotationType() {
    return 0x10;
  }
}
