package yarin.cbhlib.annotations;

import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class UnknownAnnotation extends Annotation {
    private int annotationType;
    private byte[] rawData;

    protected UnknownAnnotation(GamePosition annotationPosition, int annotationType, ByteBuffer annotationData, int length) {
        super(annotationPosition);
        this.annotationType = annotationType;
        this.rawData = new byte[length];
        annotationData.get(rawData);
    }
}
