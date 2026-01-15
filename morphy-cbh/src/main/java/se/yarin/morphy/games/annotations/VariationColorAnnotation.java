package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import se.yarin.util.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

@Value.Immutable
public abstract class VariationColorAnnotation extends Annotation {
  @Value.Parameter
  public abstract int red();

  @Value.Parameter
  public abstract int green();

  @Value.Parameter
  public abstract int blue();

  @Value.Parameter
  public abstract boolean onlyMoves();

  @Value.Parameter
  public abstract boolean onlyMainline();

  @Override
  public String toString() {
    return String.format(
        "Variation color = #%02X%02X%02X (%s, %s)",
        red(),
        green(),
        blue(),
        onlyMoves() ? "Moves only" : "Moves and annotations",
        onlyMainline() ? "Mainline only" : "Include sublines");
  }

  public static class Serializer implements AnnotationSerializer {
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
}
