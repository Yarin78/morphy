package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.*;
import se.yarin.util.ByteBufferUtil;

/** Reads and writes a {@link VariationColorAnnotation} in the v1 {@code .cba} format. */
public class VariationColorAnnotationSerializer implements AnnotationSerializer {
  @Override
  public void serialize(ByteBuffer buf, Annotation annotation) {
    VariationColorAnnotation vca = (VariationColorAnnotation) annotation;
    int flag = 0;
    if (vca.onlyMainline()) flag += 1;
    if (vca.onlyMoves()) flag += 2;
    ByteBufferUtil.putByte(buf, flag);
    ByteBufferUtil.putByte(buf, vca.blue());
    ByteBufferUtil.putByte(buf, vca.green());
    ByteBufferUtil.putByte(buf, vca.red());
  }

  @Override
  public VariationColorAnnotation deserialize(ByteBuffer buf, int length) {
    int flag = buf.get();
    boolean onlyMainline = (flag & 1) == 1;
    boolean onlyMoves = (flag & 2) == 2;

    int b = ByteBufferUtil.getUnsignedByte(buf);
    int g = ByteBufferUtil.getUnsignedByte(buf);
    int r = ByteBufferUtil.getUnsignedByte(buf);

    return ImmutableVariationColorAnnotation.of(r, g, b, onlyMoves, onlyMainline);
  }

  @Override
  public Class getAnnotationClass() {
    return ImmutableVariationColorAnnotation.class;
  }

  @Override
  public int getAnnotationType() {
    return 0x23;
  }
}
