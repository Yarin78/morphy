package se.yarin.morphy.chessbase.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.GameHeaderFlags;

import java.util.Base64;
import java.util.regex.Pattern;

@Value.Immutable
public abstract class CorrespondenceMoveAnnotation extends Annotation
    implements StatisticalAnnotation {
  @Value.Parameter
  public abstract byte[] rawData();

  @Override
  public String toString() {
    return "CorrespondenceMoveAnnotation = " + AnnotationPgnUtil.toHexString(rawData());
  }

  @Override
  public void updateStatistics(AnnotationStatistics stats) {
    stats.flags.add(GameHeaderFlags.CORRESPONDENCE_HEADER);
  }


  public static class PgnCodec implements AnnotationPgnCodec {
    private static final Pattern CORR_PATTERN = Pattern.compile("\\[%corr\\s+([A-Za-z0-9+/=]+)\\]");

    @Override
    @NotNull
    public Pattern getPattern() {
      return CORR_PATTERN;
    }

    @Override
    @Nullable
    public String encode(@NotNull Annotation annotation) {
      CorrespondenceMoveAnnotation a = (CorrespondenceMoveAnnotation) annotation;
      return "[%corr " + Base64.getEncoder().encodeToString(a.rawData()) + "]";
    }

    @Override
    @Nullable
    public Annotation decode(@NotNull String data) {
      byte[] bytes = Base64.getDecoder().decode(data);
      return ImmutableCorrespondenceMoveAnnotation.of(bytes);
    }

    @Override
    @NotNull
    public Class<? extends Annotation> getAnnotationClass() {
      return ImmutableCorrespondenceMoveAnnotation.class;
    }
  }
}
