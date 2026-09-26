package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import java.util.*;
import se.yarin.chess.NAG;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.*;
import se.yarin.util.ByteBufferUtil;

/** Reads and writes a {@link SymbolAnnotation} in the v1 {@code .cba} format. */
public class SymbolAnnotationSerializer implements AnnotationSerializer {
  @Override
  public Annotation deserialize(ByteBuffer buf, int length) {
    NAG[] symbols = new NAG[length];
    for (int i = 0; i < length; i++) {
      symbols[i] = NAG.values()[ByteBufferUtil.getUnsignedByte(buf)];
    }
    return ImmutableSymbolAnnotation.of(symbols);
  }

  @Override
  public void serialize(ByteBuffer buf, Annotation annotation) {
    SymbolAnnotation symbolAnnotation = (SymbolAnnotation) annotation;
    int b1 = symbolAnnotation.moveComment().ordinal();
    int b2 = symbolAnnotation.lineEvaluation().ordinal();
    int b3 = symbolAnnotation.movePrefix().ordinal();
    ByteBufferUtil.putByte(buf, b1);
    if (b2 != 0 || b3 != 0) ByteBufferUtil.putByte(buf, b2);
    if (b3 != 0) ByteBufferUtil.putByte(buf, b3);
  }

  @Override
  public int getAnnotationType() {
    return 0x03;
  }

  @Override
  public Class getAnnotationClass() {
    return ImmutableSymbolAnnotation.class;
  }
}
