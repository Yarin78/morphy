package yarin.cbhlib.annotations;

import java.nio.ByteBuffer;

public class InvalidAnnotation extends Annotation {
    private int annotationType;
    private byte[] rawData;

    protected InvalidAnnotation(int annotationType, ByteBuffer annotationData, int length) {
        this.annotationType = annotationType;
        this.rawData = new byte[length];
        annotationData.get(rawData);
    }
}
