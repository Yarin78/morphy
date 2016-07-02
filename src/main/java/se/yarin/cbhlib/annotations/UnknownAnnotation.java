package se.yarin.cbhlib.annotations;

import se.yarin.cbhlib.CBUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class UnknownAnnotation extends Annotation {
    private int annotationType;
    private byte[] rawData;

    public UnknownAnnotation(int annotationType, ByteBuffer annotationData, int length) {
        this.annotationType = annotationType;
        this.rawData = new byte[length];
        annotationData.get(rawData);
    }

    @Override
    public String toString() {
        return "UnknownAnnotation " + annotationType + " " + CBUtil.toHexString(rawData);
    }
}
