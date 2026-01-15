package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import se.yarin.morphy.util.CBUtil;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

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
    return "VideoAnnotation = " + CBUtil.toHexString(rawData());
  }

  @Override
  public void updateStatistics(AnnotationStatistics stats) {
    stats.flags.add(GameHeaderFlags.EMBEDDED_VIDEO);
  }

  public static class Serializer implements AnnotationSerializer {

    @Override
    public void serialize(ByteBuffer buf, Annotation annotation) {
      buf.put(((VideoAnnotation) annotation).rawData());
    }

    @Override
    public VideoAnnotation deserialize(ByteBuffer buf, int length) {
      // First byte seems to always be 1
      // Second byte is either 0x00, 0x2A or 0x35
      // Then follows a string (without any length specified)

      byte data[] = new byte[length];
      buf.get(data);

      return ImmutableVideoAnnotation.of(data);
    }

    @Override
    public Class getAnnotationClass() {
      return ImmutableVideoAnnotation.class;
    }

    @Override
    public int getAnnotationType() {
      return 0x20;
    }
  }
}
