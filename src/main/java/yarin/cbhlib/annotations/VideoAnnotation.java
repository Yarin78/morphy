package yarin.cbhlib.annotations;

import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class VideoAnnotation extends Annotation {
    public VideoAnnotation(GamePosition annotationPosition, ByteBuffer data) throws CBHFormatException {
        super(annotationPosition);
        throw new CBHFormatException("Video isn't yet supported");
    }
}
