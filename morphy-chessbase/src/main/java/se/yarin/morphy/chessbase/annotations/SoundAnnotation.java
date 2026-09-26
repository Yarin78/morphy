package se.yarin.morphy.chessbase.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.chessbase.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Sound annotations are not supported. ChessBase 13 doesn't either - probably a deprecated feature?
 */
@Deprecated
@Value.Immutable
public abstract class SoundAnnotation extends Annotation implements StatisticalAnnotation {
  @Value.Parameter
  public abstract byte[] rawData();

  @Override
  public String toString() {
    return "SoundAnnotation = " + AnnotationPgnUtil.toHexString(rawData());
  }

  @Override
  public void updateStatistics(AnnotationStatistics stats) {
    stats.flags.add(GameHeaderFlags.EMBEDDED_AUDIO);
  }


  public static class PgnCodec implements AnnotationPgnCodec {
    private static final Pattern SOUND_PATTERN = Pattern.compile("\\[%sound\\s+([A-Za-z0-9+/=]+)\\]");

    @Override
    @NotNull
    public Pattern getPattern() {
      return SOUND_PATTERN;
    }

    @Override
    @Nullable
    public String encode(@NotNull Annotation annotation) {
      SoundAnnotation a = (SoundAnnotation) annotation;
      return "[%sound " + Base64.getEncoder().encodeToString(a.rawData()) + "]";
    }

    @Override
    @Nullable
    public Annotation decode(@NotNull String data) {
      byte[] bytes = Base64.getDecoder().decode(data);
      return ImmutableSoundAnnotation.of(bytes);
    }

    @Override
    @NotNull
    public Class<? extends Annotation> getAnnotationClass() {
      return ImmutableSoundAnnotation.class;
    }
  }
}
