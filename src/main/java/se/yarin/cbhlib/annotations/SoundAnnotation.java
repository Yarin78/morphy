package se.yarin.cbhlib.annotations;

import lombok.Getter;
import se.yarin.cbhlib.CBUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

/**
 * Sound annotations are not supported.
 * ChessBase 13 doesn't either - probably a deprecated feature?
 */
@Deprecated
public class SoundAnnotation extends Annotation {
    @Getter
    private byte[] rawData;

    public SoundAnnotation(byte[] rawData) {
        this.rawData = rawData;
    }

    public static SoundAnnotation deserialize(ByteBuffer buf, int length) {
        byte data[] = new byte[length];
        buf.get(data);

        return new SoundAnnotation(data);
    }

    @Override
    public String toString() {
        return "SoundAnnotation = " + CBUtil.toHexString(rawData);
    }
}
