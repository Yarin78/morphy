package se.yarin.cbhlib.annotations;

import lombok.Getter;
import se.yarin.cbhlib.CBUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

public class TrainingAnnotation extends Annotation {
    @Getter
    private byte[] rawData;

    public TrainingAnnotation(byte[] rawData) {
        this.rawData = rawData;
    }

    public static TrainingAnnotation deserialize(ByteBuffer buf, int length) {
        // TODO: Support this
        byte data[] = new byte[length];
        buf.get(data);

        return new TrainingAnnotation(data);
    }

    @Override
    public String toString() {
        return "TrainingAnnotation = " + CBUtil.toHexString(rawData);
    }

}
