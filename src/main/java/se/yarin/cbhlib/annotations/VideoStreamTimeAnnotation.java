package se.yarin.cbhlib.annotations;

import lombok.Getter;
import se.yarin.chess.annotations.Annotation;

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
}
