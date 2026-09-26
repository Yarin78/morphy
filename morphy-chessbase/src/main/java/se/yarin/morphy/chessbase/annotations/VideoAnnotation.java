package se.yarin.morphy.chessbase.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.annotations.Annotation;

import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Video annotations are not supported. ChessBase 13 doesn't either - probably a deprecated feature?
 */
@Deprecated
@Value.Immutable
public abstract class VideoAnnotation extends Annotation implements StatisticalAnnotation {
  @Value.Parameter
  public abstract byte[] rawData();

  @Override
  public String toString() {
    return "VideoAnnotation = " + AnnotationPgnUtil.toHexString(rawData());
  }

  @Override
  public void updateStatistics(AnnotationStatistics stats) {
    // The annotation of type 0x20 doesn't set any flag. In Mega Database 2021, 22 of them are on 17
    // games, and none of these have the embedded video flag. In fact, no game in any database has
    // that flag; only guiding texts do.
  }


  public static class PgnCodec implements AnnotationPgnCodec {
    private static final Pattern VIDEO_PATTERN = Pattern.compile("\\[%video\\s+([A-Za-z0-9+/=]+)\\]");

    @Override
    @NotNull
    public Pattern getPattern() {
      return VIDEO_PATTERN;
    }

    @Override
    @Nullable
    public String encode(@NotNull Annotation annotation) {
      VideoAnnotation a = (VideoAnnotation) annotation;
      return "[%video " + Base64.getEncoder().encodeToString(a.rawData()) + "]";
    }

    @Override
    @Nullable
    public Annotation decode(@NotNull String data) {
      byte[] bytes = Base64.getDecoder().decode(data);
      return ImmutableVideoAnnotation.of(bytes);
    }

    @Override
    @NotNull
    public Class<? extends Annotation> getAnnotationClass() {
      return ImmutableVideoAnnotation.class;
    }
  }
}
