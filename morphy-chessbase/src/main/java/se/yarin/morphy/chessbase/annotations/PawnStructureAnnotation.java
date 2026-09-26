package se.yarin.morphy.chessbase.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.chessbase.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.util.regex.Pattern;

@Value.Immutable
public abstract class PawnStructureAnnotation extends Annotation implements StatisticalAnnotation {
  private static final Logger log = LoggerFactory.getLogger(PawnStructureAnnotation.class);

  @Value.Parameter
  public abstract int type(); // ?? always 3?

  @Override
  public String toString() {
    return "PawnStructureAnnotation";
  }

  @Override
  public void updateStatistics(AnnotationStatistics stats) {
    stats.flags.add(GameHeaderFlags.PAWN_STRUCTURE);
  }


  public static class PgnCodec implements AnnotationPgnCodec {
    private static final Pattern PAWNSTRUCT_PATTERN = Pattern.compile("\\[%pawnstruct\\s+([^\\]]+)\\]");

    @Override
    @NotNull
    public Pattern getPattern() {
      return PAWNSTRUCT_PATTERN;
    }

    @Override
    @Nullable
    public String encode(@NotNull Annotation annotation) {
      PawnStructureAnnotation a = (PawnStructureAnnotation) annotation;
      return "[%pawnstruct " + a.type() + "]";
    }

    @Override
    @Nullable
    public Annotation decode(@NotNull String data) {
      try {
        int type = Integer.parseInt(data.trim());
        return ImmutablePawnStructureAnnotation.of(type);
      } catch (NumberFormatException e) {
        log.warn("Invalid pawnstruct type: {}", data);
        return null;
      }
    }

    @Override
    @NotNull
    public Class<? extends Annotation> getAnnotationClass() {
      return ImmutablePawnStructureAnnotation.class;
    }
  }
}
