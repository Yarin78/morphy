package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.*;
import se.yarin.util.ByteBufferUtil;

/** Reads and writes a {@link PawnStructureAnnotation} in the v1 {@code .cba} format. */
public class PawnStructureAnnotationSerializer implements AnnotationSerializer {
  @Override
  public void serialize(ByteBuffer buf, Annotation annotation) {
    ByteBufferUtil.putByte(buf, ((PawnStructureAnnotation) annotation).type());
  }

  @Override
  public PawnStructureAnnotation deserialize(ByteBuffer buf, int length) {
    return ImmutablePawnStructureAnnotation.of(ByteBufferUtil.getUnsignedByte(buf));
  }

  @Override
  public Class getAnnotationClass() {
    return ImmutablePawnStructureAnnotation.class;
  }

  @Override
  public int getAnnotationType() {
    return 0x14;
  }
}
