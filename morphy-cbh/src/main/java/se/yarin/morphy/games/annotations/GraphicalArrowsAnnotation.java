package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.exceptions.MorphyAnnotationExecption;
import se.yarin.util.ByteBufferUtil;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.chess.Chess;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

  public static class PgnCodec implements AnnotationPgnCodec {
    private static final Logger log = LoggerFactory.getLogger(PgnCodec.class);
    private static final Pattern ARROWS_PATTERN = Pattern.compile("\\[%cal\\s+([^\\]]+)\\]");

    @Override
    @NotNull
    public Pattern getPattern() {
      return ARROWS_PATTERN;
    }

    @Override
    @Nullable
    public String encode(@NotNull Annotation annotation) {
      GraphicalArrowsAnnotation a = (GraphicalArrowsAnnotation) annotation;
      if (a.arrows().isEmpty()) {
        return "";
      }

      String arrowsStr = a.arrows().stream()
          .map(arrow -> AnnotationPgnUtil.colorToChar(arrow.color()) +
              Chess.sqiToStr(arrow.fromSqi()) +
              Chess.sqiToStr(arrow.toSqi()))
          .collect(Collectors.joining(","));

      return "[%cal " + arrowsStr + "]";
    }

    @Override
    @Nullable
    public Annotation decode(@NotNull String data) {
      List<Arrow> arrows = new ArrayList<>();

      for (String item : data.split(",")) {
        item = item.trim();
        if (item.length() < 5) {
          log.warn("Invalid arrow annotation format: {}", item);
          continue;
        }

        GraphicalAnnotationColor color = AnnotationPgnUtil.charToColor(item.charAt(0));
        if (color == null) {
          log.warn("Invalid color in arrow annotation: {}", item.charAt(0));
          continue;
        }

        String squaresStr = item.substring(1);
        if (squaresStr.length() != 4) {
          log.warn("Invalid arrow squares format: {}", squaresStr);
          continue;
        }

        String fromStr = squaresStr.substring(0, 2);
        String toStr = squaresStr.substring(2, 4);

        int fromSqi = Chess.strToSqi(fromStr);
        int toSqi = Chess.strToSqi(toStr);

        if (fromSqi == -1 || toSqi == -1) {
          log.warn("Invalid squares in arrow annotation: {} -> {}", fromStr, toStr);
          continue;
        }

        arrows.add(ImmutableArrow.of(color, fromSqi, toSqi));
      }

      if (arrows.isEmpty()) {
        return null;
      }

      return ImmutableGraphicalArrowsAnnotation.of(arrows);
    }

    @Override
    @NotNull
    public Class<? extends Annotation> getAnnotationClass() {
      return ImmutableGraphicalArrowsAnnotation.class;
    }
  }
}
