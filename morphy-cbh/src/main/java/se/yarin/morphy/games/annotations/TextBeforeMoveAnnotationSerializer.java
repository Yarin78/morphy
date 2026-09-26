package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.Nation;
import se.yarin.morphy.chessbase.annotations.*;
import se.yarin.util.ByteBufferUtil;

/** Reads and writes a {@link TextBeforeMoveAnnotation} in the v1 {@code .cba} format. */
public class TextBeforeMoveAnnotationSerializer implements AnnotationSerializer {
  @Override
  public void serialize(ByteBuffer buf, Annotation annotation) {
    TextBeforeMoveAnnotation textAnno = (TextBeforeMoveAnnotation) annotation;
    ByteBufferUtil.putByte(buf, textAnno.unknown());
    ByteBufferUtil.putByte(buf, textAnno.language().ordinal());
    ByteBufferUtil.putFixedSizeByteString(buf, textAnno.text(), textAnno.text().length());
  }

  @Override
  public TextBeforeMoveAnnotation deserialize(ByteBuffer buf, int length) {
    return ImmutableTextBeforeMoveAnnotation.builder()
        .unknown(buf.get())
        .language(Nation.values()[ByteBufferUtil.getUnsignedByte(buf)])
        .text(ByteBufferUtil.getFixedSizeByteString(buf, length - 2))
        .build();
  }

  @Override
  public Class getAnnotationClass() {
    return ImmutableTextBeforeMoveAnnotation.class;
  }

  @Override
  public int getAnnotationType() {
    return 0x82;
  }
}
