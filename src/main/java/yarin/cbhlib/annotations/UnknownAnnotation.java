package yarin.cbhlib.annotations;

import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class UnknownAnnotation extends Annotation {
    private int annotationType;
    private byte[] rawData;

    protected UnknownAnnotation(int annotationType, ByteBuffer annotationData, int length) {
        this.annotationType = annotationType;
        this.rawData = new byte[length];
        annotationData.get(rawData);
    }
}
