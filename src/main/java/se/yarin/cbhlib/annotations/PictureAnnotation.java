package se.yarin.cbhlib.annotations;

import lombok.Getter;
import se.yarin.cbhlib.CBUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

/**
 * Picture annotations are not supported.
 * ChessBase 13 doesn't either - probably a deprecated feature?
 */
@Deprecated
public class PictureAnnotation extends Annotation {
    @Getter
    private byte[] rawData;

    public PictureAnnotation(byte[] rawData) {
        this.rawData = rawData;
    }

    public static PictureAnnotation deserialize(ByteBuffer buf, int length) {
        byte data[] = new byte[length];
        buf.get(data);

        return new PictureAnnotation(data);
    }

    @Override
    public String toString() {
        return "PictureAnnotation = " + CBUtil.toHexString(rawData);
    }
}
