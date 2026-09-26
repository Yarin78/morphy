package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.*;
import se.yarin.util.ByteBufferUtil;

/** Reads and writes a {@link PiecePathAnnotation} in the v1 {@code .cba} format. */
public class PiecePathAnnotationSerializer implements AnnotationSerializer {
  @Override
  public void serialize(ByteBuffer buf, Annotation annotation) {
    PiecePathAnnotation ppa = (PiecePathAnnotation) annotation;
    ByteBufferUtil.putByte(buf, ppa.type());
    ByteBufferUtil.putByte(buf, ppa.sqi() + 1);
  }

  @Override
  public PiecePathAnnotation deserialize(ByteBuffer buf, int length) {
    return ImmutablePiecePathAnnotation.of(
        ByteBufferUtil.getUnsignedByte(buf), ByteBufferUtil.getUnsignedByte(buf) - 1);
  }

  @Override
  public Class getAnnotationClass() {
    return ImmutablePiecePathAnnotation.class;
  }

  @Override
  public int getAnnotationType() {
    return 0x15;
  }
}
