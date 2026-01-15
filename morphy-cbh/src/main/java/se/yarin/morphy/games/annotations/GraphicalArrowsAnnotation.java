package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import se.yarin.morphy.exceptions.MorphyAnnotationExecption;
import se.yarin.util.ByteBufferUtil;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.*;

@Value.Immutable
public abstract class GraphicalArrowsAnnotation extends Annotation
    implements StatisticalAnnotation {

  @Override
  public void updateStatistics(AnnotationStatistics stats) {
    stats.noGraphicalArrows++;
    stats.flags.add(GameHeaderFlags.GRAPHICAL_ARROWS);
  }

  @Value.Immutable
  public interface Arrow {
    @Value.Parameter
    GraphicalAnnotationColor color();

    @Value.Parameter
    int fromSqi();

    @Value.Parameter
    int toSqi();
  }

  @Value.Parameter
  public abstract List<Arrow> arrows();

  @Override
  public int priority() {
    return 5;
  }

  public static class Serializer implements AnnotationSerializer {
    @Override
    public void serialize(ByteBuffer buf, Annotation annotation) {
      for (Arrow arrow : ((GraphicalArrowsAnnotation) annotation).arrows()) {
        ByteBufferUtil.putByte(buf, arrow.color().getColorId());
        ByteBufferUtil.putByte(buf, arrow.fromSqi() + 1);
        ByteBufferUtil.putByte(buf, arrow.toSqi() + 1);
      }
    }

    @Override
    public GraphicalArrowsAnnotation deserialize(ByteBuffer buf, int length)
        throws MorphyAnnotationExecption {
      ArrayList<Arrow> arrows = new ArrayList<>();
      for (int i = 0; i < length / 3; i++) {
        int color = ByteBufferUtil.getUnsignedByte(buf);
        int fromSqi = ByteBufferUtil.getUnsignedByte(buf) - 1;
        int toSqi = ByteBufferUtil.getUnsignedByte(buf) - 1;
        if (fromSqi < 0
            || fromSqi > 63
            || toSqi < 0
            || toSqi > 63
            || color < 0
            || color > GraphicalAnnotationColor.maxColor())
          throw new MorphyAnnotationExecption("Invalid graphical arrows annotation");
        arrows.add(ImmutableArrow.of(GraphicalAnnotationColor.fromInt(color), fromSqi, toSqi));
      }
      return ImmutableGraphicalArrowsAnnotation.of(arrows);
    }

    @Override
    public Class getAnnotationClass() {
      return ImmutableGraphicalArrowsAnnotation.class;
    }

    @Override
    public int getAnnotationType() {
      return 0x05;
    }
  }
}
