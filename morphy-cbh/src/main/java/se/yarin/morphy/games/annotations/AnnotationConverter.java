package se.yarin.morphy.games.annotations;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.NAG;
import se.yarin.chess.NAGType;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.annotations.Annotations;
import se.yarin.chess.annotations.CommentaryAfterMoveAnnotation;
import se.yarin.chess.annotations.CommentaryBeforeMoveAnnotation;
import se.yarin.chess.annotations.NAGAnnotation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Converts generic annotations (from PGN parsing) to storage annotations (for ChessBase database).
 *
 * This is necessary before saving a game to the database, as generic annotations might
 * not have registered annotation serializers.
 *
 * <p>This converter handles the translation layer between:
 * <ul>
 *   <li>{@link NAGAnnotation} → {@link SymbolAnnotation} (with NAG consolidation)</li>
 *   <li>{@link CommentaryAfterMoveAnnotation} → {@link TextAfterMoveAnnotation}</li>
 *   <li>{@link CommentaryBeforeMoveAnnotation} → {@link TextBeforeMoveAnnotation}</li>
 * </ul>
 *
 * <p>NAG Consolidation: ChessBase {@link SymbolAnnotation} can store up to 3 NAGs (one of each type:
 * MOVE_COMMENT, LINE_EVALUATION, MOVE_PREFIX). If multiple NAGs of the same type exist on a node,
 * only the first one is kept and a warning is logged about the data loss.
 *
 * <p>Usage: Call {@link #convertAnnotations(GameMovesModel)} before saving a game to the database
 * to ensure all annotations are in the correct storage format.
 */
public class AnnotationConverter {
    private static final Logger log = LoggerFactory.getLogger(AnnotationConverter.class);

    /**
     * Converts all generic annotations in a game to storage annotations.
     * This method traverses the entire move tree including all variations.
     *
     * @param moves the game moves model to convert annotations for
     */
    public static void convertAnnotations(@NotNull GameMovesModel moves) {
        moves.root().traverseDepthFirst(node -> convertNodeAnnotations(node.getAnnotations()));
    }

    /**
     * Converts annotations on a single node.
     *
     * @param annotations the annotations collection to convert
     */
    private static void convertNodeAnnotations(@NotNull Annotations annotations) {
        if (annotations.isEmpty()) {
            return;
        }

        // Collect generic annotations that need conversion
        List<NAGAnnotation> nagAnnotations = new ArrayList<>();
        CommentaryAfterMoveAnnotation afterMove = null;
        CommentaryBeforeMoveAnnotation beforeMove = null;

        for (Annotation annotation : annotations) {
            if (annotation instanceof NAGAnnotation) {
                nagAnnotations.add((NAGAnnotation) annotation);
            } else if (annotation instanceof CommentaryAfterMoveAnnotation) {
                afterMove = (CommentaryAfterMoveAnnotation) annotation;
            } else if (annotation instanceof CommentaryBeforeMoveAnnotation) {
                beforeMove = (CommentaryBeforeMoveAnnotation) annotation;
            }
        }

        // Convert NAG annotations if present
        if (!nagAnnotations.isEmpty()) {
            convertNAGAnnotations(annotations, nagAnnotations);
        }

        // Convert commentary after move
        if (afterMove != null) {
            annotations.remove(afterMove);
            annotations.add(ImmutableTextAfterMoveAnnotation.of(afterMove.getCommentary()));
        }

        // Convert commentary before move
        if (beforeMove != null) {
            annotations.remove(beforeMove);
            annotations.add(ImmutableTextBeforeMoveAnnotation.of(beforeMove.getCommentary()));
        }
    }

    /**
     * Consolidates multiple NAG annotations into a single SymbolAnnotation.
     * ChessBase format supports exactly 3 NAGs: one MOVE_COMMENT, one LINE_EVALUATION, and one MOVE_PREFIX.
     * If multiple NAGs of the same type exist, only the first one is kept and a warning is logged.
     *
     * @param annotations the annotations collection to modify
     * @param nagAnnotations the list of NAG annotations to consolidate
     */
    private static void convertNAGAnnotations(@NotNull Annotations annotations, @NotNull List<NAGAnnotation> nagAnnotations) {
        // Group NAGs by type, keeping only the first of each type
        Map<NAGType, NAG> nagsByType = new EnumMap<>(NAGType.class);

        for (NAGAnnotation nagAnnotation : nagAnnotations) {
            NAG nag = nagAnnotation.getNag();
            NAGType type = nag.getType();

            if (type == NAGType.NONE) {
                // Skip NAGs with no type
                log.debug("Skipping NAG with NONE type: {}", nag);
                continue;
            }

            if (nagsByType.containsKey(type)) {
                // Multiple NAGs of same type - keep first, warn about data loss
                log.warn("Multiple NAGs of type {} found on same node. Keeping {} and dropping {}. " +
                         "ChessBase format only supports one NAG per type.",
                         type, nagsByType.get(type), nag);
            } else {
                nagsByType.put(type, nag);
            }
        }

        // Remove all NAG annotations
        annotations.removeAll(nagAnnotations);

        // Create consolidated SymbolAnnotation if we have any NAGs
        if (!nagsByType.isEmpty()) {
            NAG moveComment = nagsByType.getOrDefault(NAGType.MOVE_COMMENT, NAG.NONE);
            NAG lineEvaluation = nagsByType.getOrDefault(NAGType.LINE_EVALUATION, NAG.NONE);
            NAG movePrefix = nagsByType.getOrDefault(NAGType.MOVE_PREFIX, NAG.NONE);

            annotations.add(ImmutableSymbolAnnotation.of(moveComment, movePrefix, lineEvaluation));
        }
    }
}
