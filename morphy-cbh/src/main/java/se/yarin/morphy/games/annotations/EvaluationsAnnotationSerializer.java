package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.EvaluationsAnnotation;
import se.yarin.morphy.chessbase.annotations.EvaluationsAnnotation.Evaluation;
import se.yarin.morphy.chessbase.annotations.ImmutableEvaluationsAnnotation;
import se.yarin.morphy.exceptions.MorphyAnnotationExecption;
import se.yarin.util.ByteBufferUtil;

/** Reads and writes an {@link EvaluationsAnnotation} in the v1 {@code .cba} format. */
public class EvaluationsAnnotationSerializer implements AnnotationSerializer {
  @Override
  public void serialize(ByteBuffer buf, Annotation annotation) {
    EvaluationsAnnotation evaluations = (EvaluationsAnnotation) annotation;
    ByteBufferUtil.putShortB(buf, evaluations.evaluations().size());
    for (Evaluation evaluation : evaluations.evaluations()) {
      ByteBufferUtil.putByte(buf, evaluation.type());
      ByteBufferUtil.putByte(buf, evaluation.depth());
      ByteBufferUtil.putShortB(buf, evaluation.eval());
    }
  }

  @Override
  public EvaluationsAnnotation deserialize(ByteBuffer buf, int length)
      throws MorphyAnnotationExecption {
    int count = ByteBufferUtil.getUnsignedShortB(buf);
    if (length != 2 + 4 * count) {
      throw new MorphyAnnotationExecption(
          String.format("Evaluations annotation of %d bytes has %d evaluations", length, count));
    }
    ArrayList<Evaluation> evaluations = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      int type = ByteBufferUtil.getUnsignedByte(buf);
      int depth = ByteBufferUtil.getUnsignedByte(buf);
      int eval = ByteBufferUtil.getSignedShortB(buf);
      evaluations.add(new Evaluation(eval, depth, type));
    }
    return ImmutableEvaluationsAnnotation.of(evaluations);
  }

  @Override
  public Class getAnnotationClass() {
    return ImmutableEvaluationsAnnotation.class;
  }

  @Override
  public int getAnnotationType() {
    return 0x26;
  }
}
