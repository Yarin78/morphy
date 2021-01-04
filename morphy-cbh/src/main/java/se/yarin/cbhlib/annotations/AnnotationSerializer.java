package se.yarin.cbhlib.annotations;

import se.yarin.cbhlib.annotations.ChessBaseAnnotationException;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

public interface AnnotationSerializer {
    void serialize(ByteBuffer buf, Annotation annotation);
    Annotation deserialize(ByteBuffer buf, int length) throws ChessBaseAnnotationException;
    Class getAnnotationClass();
    int getAnnotationType();
}
