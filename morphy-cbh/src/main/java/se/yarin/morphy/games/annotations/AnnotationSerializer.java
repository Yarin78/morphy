package se.yarin.morphy.games.annotations;

import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.exceptions.MorphyAnnotationExecption;

import java.nio.ByteBuffer;

public interface AnnotationSerializer {
    void serialize(ByteBuffer buf, Annotation annotation);
    Annotation deserialize(ByteBuffer buf, int length) throws MorphyAnnotationExecption;
    Class getAnnotationClass();
    int getAnnotationType();
}
