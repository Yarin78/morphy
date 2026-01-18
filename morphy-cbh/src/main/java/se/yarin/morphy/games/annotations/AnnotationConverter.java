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
 * Bidirectional converter between generic annotations and storage annotations.
 *
 * <p>This converter handles the translation layer in both directions:
 * <ul>
 *   <li><b>To Storage (for database save):</b>
 *     <ul>
 *       <li>{@link NAGAnnotation} → {@link SymbolAnnotation} (with NAG consolidation)</li>
 *       <li>{@link CommentaryAfterMoveAnnotation} → {@link TextAfterMoveAnnotation}</li>
 *       <li>{@link CommentaryBeforeMoveAnnotation} → {@link TextBeforeMoveAnnotation}</li>
 *     </ul>
 *   </li>
 *   <li><b>To Generic (for PGN export/display):</b>
 *     <ul>
 *       <li>{@link SymbolAnnotation} → multiple {@link NAGAnnotation} (one per NAG type)</li>
 *       <li>{@link TextAfterMoveAnnotation} → {@link CommentaryAfterMoveAnnotation}</li>
 *       <li>{@link TextBeforeMoveAnnotation} → {@link CommentaryBeforeMoveAnnotation}</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>NAG Consolidation (to storage): ChessBase {@link SymbolAnnotation} can store up to 3 NAGs
 * (one of each type: MOVE_COMMENT, LINE_EVALUATION, MOVE_PREFIX). If multiple NAGs of the same
 * type exist on a node, only the first one is kept and a warning is logged.
 *
 * <p>NAG Expansion (to generic): A {@link SymbolAnnotation} is expanded into separate
 * {@link NAGAnnotation} objects for each non-NONE NAG it contains.
 *
 * <p>Usage:
 * <ul>
 *   <li>Call {@link #convertToStorageAnnotations(GameMovesModel)} before saving to database</li>
 *   <li>Call {@link #convertToGenericAnnotations(GameMovesModel)} after loading from database or before PGN export</li>
 * </ul>
 */
public class AnnotationConverter {
    private static final Logger log = LoggerFactory.getLogger(AnnotationConverter.class);

    /**
     * Converts all generic annotations in a game to storage annotations.
     * This method traverses the entire move tree including all variations.
     *
     * <p>Use this before saving a game to the database.
     *
     * @param moves the game moves model to convert annotations for
     */
    public static void convertToStorageAnnotations(@NotNull GameMovesModel moves) {
        moves.root().traverseDepthFirst(node -> convertNodeToStorageAnnotations(node.getAnnotations()));
    }

    /**
     * Converts all storage annotations in a game to generic annotations.
     * This method traverses the entire move tree including all variations.
     *
     * <p>Use this after loading a game from the database or before exporting to PGN.
     *
     * @param moves the game moves model to convert annotations for
     */
    public static void convertToGenericAnnotations(@NotNull GameMovesModel moves) {
        moves.root().traverseDepthFirst(node -> convertNodeToGenericAnnotations(node.getAnnotations()));
    }

    /**
     * Legacy method for backward compatibility. Delegates to {@link #convertToStorageAnnotations(GameMovesModel)}.
     *
     * @param moves the game moves model to convert annotations for
     * @deprecated Use {@link #convertToStorageAnnotations(GameMovesModel)} instead for clarity
     */
    @Deprecated
    public static void convertAnnotations(@NotNull GameMovesModel moves) {
        convertToStorageAnnotations(moves);
    }

    /**
     * Converts annotations on a single node from generic to storage format.
     *
     * @param annotations the annotations collection to convert
     */
    private static void convertNodeToStorageAnnotations(@NotNull Annotations annotations) {
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
            convertNAGAnnotationsToStorage(annotations, nagAnnotations);
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
     * Converts annotations on a single node from storage to generic format.
     *
     * @param annotations the annotations collection to convert
     */
    private static void convertNodeToGenericAnnotations(@NotNull Annotations annotations) {
        if (annotations.isEmpty()) {
            return;
        }

        // Collect storage annotations that need conversion
        List<SymbolAnnotation> symbolAnnotations = new ArrayList<>();
        TextAfterMoveAnnotation afterMove = null;
        TextBeforeMoveAnnotation beforeMove = null;

        for (Annotation annotation : annotations) {
            if (annotation instanceof SymbolAnnotation) {
                symbolAnnotations.add((SymbolAnnotation) annotation);
            } else if (annotation instanceof TextAfterMoveAnnotation) {
                afterMove = (TextAfterMoveAnnotation) annotation;
            } else if (annotation instanceof TextBeforeMoveAnnotation) {
                beforeMove = (TextBeforeMoveAnnotation) annotation;
            }
        }

        // Convert SymbolAnnotation to NAGAnnotations
        for (SymbolAnnotation symbolAnnotation : symbolAnnotations) {
            annotations.remove(symbolAnnotation);
            convertSymbolAnnotationToNAGs(annotations, symbolAnnotation);
        }

        // Convert text after move
        if (afterMove != null) {
            annotations.remove(afterMove);
            annotations.add(new CommentaryAfterMoveAnnotation(afterMove.text()));
        }

        // Convert text before move
        if (beforeMove != null) {
            annotations.remove(beforeMove);
            annotations.add(new CommentaryBeforeMoveAnnotation(beforeMove.text()));
        }
    }

    /**
     * Expands a SymbolAnnotation into individual NAGAnnotation objects.
     *
     * @param annotations the annotations collection to add to
     * @param symbolAnnotation the symbol annotation to expand
     */
    private static void convertSymbolAnnotationToNAGs(@NotNull Annotations annotations, @NotNull SymbolAnnotation symbolAnnotation) {
        // Add NAG for move comment if present
        if (symbolAnnotation.moveComment() != NAG.NONE) {
            annotations.add(new NAGAnnotation(symbolAnnotation.moveComment()));
        }

        // Add NAG for line evaluation if present
        if (symbolAnnotation.lineEvaluation() != NAG.NONE) {
            annotations.add(new NAGAnnotation(symbolAnnotation.lineEvaluation()));
        }

        // Add NAG for move prefix if present
        if (symbolAnnotation.movePrefix() != NAG.NONE) {
            annotations.add(new NAGAnnotation(symbolAnnotation.movePrefix()));
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
    private static void convertNAGAnnotationsToStorage(@NotNull Annotations annotations, @NotNull List<NAGAnnotation> nagAnnotations) {
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
