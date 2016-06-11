package yarin.cbhlib.annotations;

import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class VideoAnnotation extends Annotation {
    public VideoAnnotation(ByteBuffer data) throws CBHFormatException {
        throw new CBHFormatException("Video isn't yet supported");
    }
}
