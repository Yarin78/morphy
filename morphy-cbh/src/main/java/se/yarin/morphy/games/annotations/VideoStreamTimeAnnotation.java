package se.yarin.morphy.games.annotations;

import lombok.Getter;
import se.yarin.util.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

/**
 * Annotation marking when a move was selected in a video stream
 */
public class VideoStreamTimeAnnotation extends Annotation {

    @Getter
    private int time;

    public VideoStreamTimeAnnotation(int millis) {
        this.time = millis;
    }

    @Override
    public String toString() {
        return "VideoStreamTimeAnnotation at " + time;
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            ByteBufferUtil.putIntB(buf, ((VideoStreamTimeAnnotation) annotation).getTime());
        }

        @Override
        public VideoStreamTimeAnnotation deserialize(ByteBuffer buf, int length) {
            return new VideoStreamTimeAnnotation(ByteBufferUtil.getIntB(buf));
        }

        @Override
        public Class getAnnotationClass() {
            return VideoStreamTimeAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x25;
        }
    }
}
