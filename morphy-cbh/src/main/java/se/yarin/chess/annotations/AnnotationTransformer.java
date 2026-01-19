package se.yarin.chess.annotations;

/**
 * A functional interface for transforming annotations in place.
 * This is typically used to convert between generic annotations (used in PGN)
 * and storage annotations (used in ChessBase databases).
 */
@FunctionalInterface
public interface AnnotationTransformer {
    /**
     * Transforms the annotations in place.
     * @param annotations the annotations to transform
     */
    void transform(Annotations annotations);
}
