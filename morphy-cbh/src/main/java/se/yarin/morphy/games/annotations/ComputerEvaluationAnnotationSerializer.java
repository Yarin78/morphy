package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.*;
import se.yarin.util.ByteBufferUtil;

/** Reads and writes a {@link ComputerEvaluationAnnotation} in the v1 {@code .cba} format. */
public class ComputerEvaluationAnnotationSerializer implements AnnotationSerializer {
  @Override
  public void serialize(ByteBuffer buf, Annotation annotation) {
    ComputerEvaluationAnnotation cea = (ComputerEvaluationAnnotation) annotation;
    ByteBufferUtil.putShortL(buf, cea.eval());
    ByteBufferUtil.putShortL(buf, cea.evalType());
    ByteBufferUtil.putShortL(buf, cea.ply());
  }

  @Override
  public ComputerEvaluationAnnotation deserialize(ByteBuffer buf, int length) {
    short eval = ByteBufferUtil.getSignedShortL(buf);
    short type = ByteBufferUtil.getSignedShortL(buf);
    short depth = ByteBufferUtil.getSignedShortL(buf);
    return ImmutableComputerEvaluationAnnotation.of(eval, type, depth);
  }

  @Override
  public Class getAnnotationClass() {
    return ImmutableComputerEvaluationAnnotation.class;
  }

  @Override
  public int getAnnotationType() {
    return 0x21;
  }
}
