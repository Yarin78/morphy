package se.yarin.chess.annotations;

import org.jetbrains.annotations.Nullable;
import se.yarin.chess.Player;

/**
 * A functional interface for transforming annotations in place.
 * This is typically used to convert between generic annotations (used in PGN)
 * and storage annotations (used in ChessBase databases).
 */
@FunctionalInterface
public interface AnnotationTransformer {
    /**
     * Transforms the annotations in place.
     *
     * @param annotations the annotations to transform
     * @param lastMoveBy the player who made the last move (for context-aware transformations
     *                   like clock annotations), or null if unknown
     */
    void transform(Annotations annotations, @Nullable Player lastMoveBy);
}
