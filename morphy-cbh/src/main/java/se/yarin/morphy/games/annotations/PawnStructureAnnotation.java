package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.util.ByteBufferUtil;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
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

  public static class Serializer implements AnnotationSerializer {
    @Override
    public void serialize(ByteBuffer buf, Annotation annotation) {
      ByteBufferUtil.putByte(buf, ((PawnStructureAnnotation) annotation).type());
    }

    @Override
    public PawnStructureAnnotation deserialize(ByteBuffer buf, int length) {
      return ImmutablePawnStructureAnnotation.of(ByteBufferUtil.getUnsignedByte(buf));
    }

    @Override
    public Class getAnnotationClass() {
      return ImmutablePawnStructureAnnotation.class;
    }

    @Override
    public int getAnnotationType() {
      return 0x14;
    }
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
