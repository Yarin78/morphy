package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import se.yarin.util.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

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
}
