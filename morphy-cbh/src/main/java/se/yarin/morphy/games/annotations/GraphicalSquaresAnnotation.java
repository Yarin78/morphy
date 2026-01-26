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
public abstract class GraphicalSquaresAnnotation extends Annotation
    implements StatisticalAnnotation {

  @Override
  public void updateStatistics(AnnotationStatistics stats) {
    stats.noGraphicalSquares++;
    stats.flags.add(GameHeaderFlags.GRAPHICAL_SQUARES);
  }

  @Value.Immutable
  public interface Square {
    @Value.Parameter
    GraphicalAnnotationColor color();

    @Value.Parameter
    int sqi();
  }

  @Value.Parameter
  public abstract List<Square> squares();

  public static class Serializer implements AnnotationSerializer {
    @Override
    public void serialize(ByteBuffer buf, Annotation annotation) {
      for (Square square : ((GraphicalSquaresAnnotation) annotation).squares()) {
        ByteBufferUtil.putByte(buf, square.color().getColorId());
        ByteBufferUtil.putByte(buf, square.sqi() + 1);
      }
    }

    @Override
    public GraphicalSquaresAnnotation deserialize(ByteBuffer buf, int length)
        throws MorphyAnnotationExecption {
      ArrayList<Square> squares = new ArrayList<>();
      for (int i = 0; i < length / 2; i++) {
        int color = ByteBufferUtil.getUnsignedByte(buf);
        int sqi = ByteBufferUtil.getUnsignedByte(buf) - 1;
        if (sqi < 0 || sqi > 63 || color < 0 || color > GraphicalAnnotationColor.maxColor())
          throw new MorphyAnnotationExecption("Invalid graphical squares annotation");
        squares.add(ImmutableSquare.of(GraphicalAnnotationColor.fromInt(color), sqi));
      }
      return ImmutableGraphicalSquaresAnnotation.of(squares);
    }

    @Override
    public Class getAnnotationClass() {
      return ImmutableGraphicalSquaresAnnotation.class;
    }

    @Override
    public int getAnnotationType() {
      return 0x04;
    }
  }

  public static class PgnCodec implements AnnotationPgnCodec {
    private static final Logger log = LoggerFactory.getLogger(PgnCodec.class);
    private static final Pattern SQUARES_PATTERN = Pattern.compile("\\[%csl\\s+([^\\]]+)\\]");

    @Override
    @NotNull
    public Pattern getPattern() {
      return SQUARES_PATTERN;
    }

    @Override
    @Nullable
    public String encode(@NotNull Annotation annotation) {
      GraphicalSquaresAnnotation a = (GraphicalSquaresAnnotation) annotation;
      if (a.squares().isEmpty()) {
        return "";
      }

      String squaresStr = a.squares().stream()
          .map(square -> AnnotationPgnUtil.colorToChar(square.color()) + Chess.sqiToStr(square.sqi()))
          .collect(Collectors.joining(","));

      return "[%csl " + squaresStr + "]";
    }

    @Override
    @Nullable
    public Annotation decode(@NotNull String data) {
      List<Square> squares = new ArrayList<>();

      for (String item : data.split(",")) {
        item = item.trim();
        if (item.length() < 3) {
          log.warn("Invalid square annotation format: {}", item);
          continue;
        }

        GraphicalAnnotationColor color = AnnotationPgnUtil.charToColor(item.charAt(0));
        if (color == null) {
          log.warn("Invalid color in square annotation: {}", item.charAt(0));
          continue;
        }

        String squareStr = item.substring(1);
        int sqi = Chess.strToSqi(squareStr);
        if (sqi == -1) {
          log.warn("Invalid square in square annotation: {}", squareStr);
          continue;
        }

        squares.add(ImmutableSquare.of(color, sqi));
      }

      if (squares.isEmpty()) {
        return null;
      }

      return ImmutableGraphicalSquaresAnnotation.of(squares);
    }

    @Override
    @NotNull
    public Class<? extends Annotation> getAnnotationClass() {
      return ImmutableGraphicalSquaresAnnotation.class;
    }
  }
}
