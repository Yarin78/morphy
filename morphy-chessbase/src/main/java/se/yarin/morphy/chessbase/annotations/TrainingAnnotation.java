package se.yarin.morphy.chessbase.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.chessbase.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.util.Base64;
import java.util.regex.Pattern;

@Value.Immutable
public abstract class TrainingAnnotation extends Annotation implements StatisticalAnnotation {
  @Value.Parameter
  public abstract byte[] rawData();

  @Override
  public String toString() {
    return "TrainingAnnotation = " + AnnotationPgnUtil.toHexString(rawData());
  }

  @Override
  public void updateStatistics(AnnotationStatistics stats) {
    stats.noTraining++;
    stats.flags.add(GameHeaderFlags.TRAINING);
  }


  public static class PgnCodec implements AnnotationPgnCodec {
    private static final Pattern TRAIN_PATTERN = Pattern.compile("\\[%train\\s+([A-Za-z0-9+/=]+)\\]");

    @Override
    @NotNull
    public Pattern getPattern() {
      return TRAIN_PATTERN;
    }

    @Override
    @Nullable
    public String encode(@NotNull Annotation annotation) {
      TrainingAnnotation a = (TrainingAnnotation) annotation;
      return "[%train " + Base64.getEncoder().encodeToString(a.rawData()) + "]";
    }

    @Override
    @Nullable
    public Annotation decode(@NotNull String data) {
      byte[] bytes = Base64.getDecoder().decode(data);
      return ImmutableTrainingAnnotation.of(bytes);
    }

    @Override
    @NotNull
    public Class<? extends Annotation> getAnnotationClass() {
      return ImmutableTrainingAnnotation.class;
    }
  }
}
