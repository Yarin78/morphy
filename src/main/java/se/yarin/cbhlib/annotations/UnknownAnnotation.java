package se.yarin.cbhlib.annotations;

import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

public class UnknownAnnotation extends Annotation {
    private int annotationType;
    private byte[] rawData;

    public UnknownAnnotation(int annotationType, ByteBuffer annotationData, int length) {
        this.annotationType = annotationType;
        this.rawData = new byte[length];
        annotationData.get(rawData);
    }
}
