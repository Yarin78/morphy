package se.yarin.cbhlib.annotations;

import lombok.Getter;
import se.yarin.cbhlib.CBUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

/**
 * Video annotations are not supported.
 * ChessBase 13 doesn't either - probably a deprecated feature?
 */
@Deprecated
public class VideoAnnotation extends Annotation {
    @Getter
    private byte[] rawData;

    public VideoAnnotation(byte[] rawData) {
        this.rawData = rawData;
    }

    public static VideoAnnotation deserialize(ByteBuffer buf, int length) {
        // First byte seems to always be 1
        // Second byte is either 0x00, 0x2A or 0x35
        // Then follows a string (without any length specified)

        byte data[] = new byte[length];
        buf.get(data);

        return new VideoAnnotation(data);
    }

    @Override
    public String toString() {
        return "VideoAnnotation = " + CBUtil.toHexString(rawData);
    }
}
