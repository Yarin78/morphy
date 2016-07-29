package se.yarin.cbhlib.annotations;

import lombok.Getter;
import se.yarin.cbhlib.CBUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

public class CorrespondenceMoveAnnotation extends Annotation {
    @Getter
    private byte[] rawData;

    public CorrespondenceMoveAnnotation(byte[] rawData) {
        this.rawData = rawData;
    }

    public static CorrespondenceMoveAnnotation deserialize(ByteBuffer buf, int length) {
        // TODO: Support this
        byte data[] = new byte[length];
        buf.get(data);

        return new CorrespondenceMoveAnnotation(data);
    }

    @Override
    public String toString() {
        return "CorrespondenceMoveAnnotation = " + CBUtil.toHexString(rawData);
    }

}
