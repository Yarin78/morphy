package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.util.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.regex.Pattern;

/** Annotation marking when a move was selected in a video stream */
@Value.Immutable
public abstract class VideoStreamTimeAnnotation extends Annotation {

  @Value.Parameter
  public abstract int time();

  @Override
  public String toString() {
    return "VideoStreamTimeAnnotation at " + time();
  }

  public static class Serializer implements AnnotationSerializer {
    @Override
    public void serialize(ByteBuffer buf, Annotation annotation) {
      ByteBufferUtil.putIntB(buf, ((VideoStreamTimeAnnotation) annotation).time());
    }

    @Override
    public VideoStreamTimeAnnotation deserialize(ByteBuffer buf, int length) {
      return ImmutableVideoStreamTimeAnnotation.of(ByteBufferUtil.getIntB(buf));
    }

    @Override
    public Class getAnnotationClass() {
      return ImmutableVideoStreamTimeAnnotation.class;
    }

    @Override
    public int getAnnotationType() {
      return 0x25;
    }
  }

  public static class PgnCodec implements AnnotationPgnCodec {
    private static final Logger log = LoggerFactory.getLogger(PgnCodec.class);
    private static final Pattern VST_PATTERN = Pattern.compile("\\[%vst\\s+([^\\]]+)\\]");

    @Override
    @NotNull
    public Pattern getPattern() {
      return VST_PATTERN;
    }

    @Override
    @Nullable
    public String encode(@NotNull Annotation annotation) {
      VideoStreamTimeAnnotation a = (VideoStreamTimeAnnotation) annotation;
      return "[%vst " + a.time() + "]";
    }

    @Override
    @Nullable
    public Annotation decode(@NotNull String data) {
      try {
        int time = Integer.parseInt(data.trim());
        return ImmutableVideoStreamTimeAnnotation.of(time);
      } catch (NumberFormatException e) {
        log.warn("Invalid vst time: {}", data);
        return null;
      }
    }

    @Override
    @NotNull
    public Class<? extends Annotation> getAnnotationClass() {
      return ImmutableVideoStreamTimeAnnotation.class;
    }
  }
}
