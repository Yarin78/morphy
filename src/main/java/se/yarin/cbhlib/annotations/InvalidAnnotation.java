package se.yarin.cbhlib.annotations;

import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

public class InvalidAnnotation extends Annotation {
    private int annotationType;
    private byte[] rawData;

    public InvalidAnnotation(int annotationType, ByteBuffer annotationData, int length) {
        this.annotationType = annotationType;
        this.rawData = new byte[length];
        annotationData.get(rawData);
    }
}
