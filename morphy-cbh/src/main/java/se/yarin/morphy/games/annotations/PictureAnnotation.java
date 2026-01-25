package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.util.CBUtil;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Picture annotations are not supported. ChessBase 13 doesn't either - probably a deprecated
 * feature?
 */
@Deprecated
@Value.Immutable
public abstract class PictureAnnotation extends Annotation implements StatisticalAnnotation {

  @Value.Parameter
  public abstract byte[] rawData();

  @Override
  public String toString() {
    return "PictureAnnotation = " + CBUtil.toHexString(rawData());
  }

  @Override
  public void updateStatistics(AnnotationStatistics stats) {
    stats.flags.add(GameHeaderFlags.EMBEDDED_PICTURE);
  }

  public static class Serializer implements AnnotationSerializer {
    @Override
    public void serialize(ByteBuffer buf, Annotation annotation) {
      buf.put(((PictureAnnotation) annotation).rawData());
    }

    @Override
    public PictureAnnotation deserialize(ByteBuffer buf, int length) {
      byte data[] = new byte[length];
      buf.get(data);

      return ImmutablePictureAnnotation.of(data);
    }

    @Override
    public Class getAnnotationClass() {
      return ImmutablePictureAnnotation.class;
    }

    @Override
    public int getAnnotationType() {
      return 0x11;
    }
  }

  public static class PgnCodec implements AnnotationPgnCodec {
    private static final Pattern PICTURE_PATTERN = Pattern.compile("\\[%picture\\s+([A-Za-z0-9+/=]+)\\]");

    @Override
    @NotNull
    public Pattern getPattern() {
      return PICTURE_PATTERN;
    }

    @Override
    @Nullable
    public String encode(@NotNull Annotation annotation) {
      PictureAnnotation a = (PictureAnnotation) annotation;
      return "[%picture " + Base64.getEncoder().encodeToString(a.rawData()) + "]";
    }

    @Override
    @Nullable
    public Annotation decode(@NotNull String data) {
      byte[] bytes = Base64.getDecoder().decode(data);
      return ImmutablePictureAnnotation.of(bytes);
    }

    @Override
    @NotNull
    public Class<? extends Annotation> getAnnotationClass() {
      return ImmutablePictureAnnotation.class;
    }
  }
}
