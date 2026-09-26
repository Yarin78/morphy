package se.yarin.morphy.chessbase.annotations;

import org.immutables.value.Value;
import se.yarin.chess.annotations.Annotation;

@Value.Immutable
public abstract class InvalidAnnotation extends Annotation implements RawAnnotation {
  @Value.Parameter
  public abstract int annotationType();

  @Value.Parameter
  public abstract byte[] rawData();

  @Override
  public String toString() {
    return String.format(
        "InvalidAnnotation %02x: %s", annotationType(), AnnotationPgnUtil.toHexString(rawData()));
  }
}
