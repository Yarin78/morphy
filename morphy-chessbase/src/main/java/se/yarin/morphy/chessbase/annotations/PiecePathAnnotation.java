package se.yarin.morphy.chessbase.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.chessbase.GameHeaderFlags;
import se.yarin.chess.Chess;
import se.yarin.chess.annotations.Annotation;

import java.util.regex.Pattern;

@Value.Immutable
public abstract class PiecePathAnnotation extends Annotation implements StatisticalAnnotation {
  private static final Logger log = LoggerFactory.getLogger(PiecePathAnnotation.class);

  @Value.Parameter
  public abstract int type(); // ?? always 3?

  @Value.Parameter
  public abstract int sqi();

  @Override
  public String toString() {
    return "PiecePathAnnotation: " + Chess.sqiToStr(sqi());
  }

  @Override
  public void updateStatistics(AnnotationStatistics stats) {
    stats.flags.add(GameHeaderFlags.PIECE_PATH);
  }


  public static class PgnCodec implements AnnotationPgnCodec {
    private static final Logger log = LoggerFactory.getLogger(PgnCodec.class);
    private static final Pattern PATH_PATTERN = Pattern.compile("\\[%path\\s+([^\\]]+)\\]");

    @Override
    @NotNull
    public Pattern getPattern() {
      return PATH_PATTERN;
    }

    @Override
    @Nullable
    public String encode(@NotNull Annotation annotation) {
      PiecePathAnnotation a = (PiecePathAnnotation) annotation;
      return "[%path " + Chess.sqiToStr(a.sqi()) + " " + a.type() + "]";
    }

    @Override
    @Nullable
    public Annotation decode(@NotNull String data) {
      try {
        String[] parts = data.split("\\s+");
        int sqi = Chess.strToSqi(parts[0]);
        int type = parts.length > 1 ? Integer.parseInt(parts[1]) : 3;
        if (sqi < 0) {
          log.warn("Invalid square in path annotation: {}", parts[0]);
          return null;
        }
        return ImmutablePiecePathAnnotation.of(type, sqi);
      } catch (Exception e) {
        log.warn("Failed to parse path: {}", data);
        return null;
      }
    }

    @Override
    @NotNull
    public Class<? extends Annotation> getAnnotationClass() {
      return ImmutablePiecePathAnnotation.class;
    }
  }
}
