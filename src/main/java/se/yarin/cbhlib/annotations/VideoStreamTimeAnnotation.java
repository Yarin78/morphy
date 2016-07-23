package se.yarin.cbhlib.annotations;

import lombok.Getter;
import se.yarin.cbhlib.ByteBufferUtil;
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

    public static VideoStreamTimeAnnotation deserialize(ByteBuffer buf) {
        return new VideoStreamTimeAnnotation(ByteBufferUtil.getIntB(buf));
    }
}
