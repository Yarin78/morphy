package se.yarin.morphy.games.annotations;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.Chess;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

        // Convert commentary after move, parsing out any graphical annotations
        if (afterMove != null) {
            annotations.remove(afterMove);

            // Parse and extract graphical annotations from the commentary text
            String remainingText = parseAndExtractGraphicalAnnotations(annotations, afterMove.getCommentary());

            // Only create TextAfterMoveAnnotation if there's remaining text
            if (!remainingText.isEmpty()) {
                annotations.add(ImmutableTextAfterMoveAnnotation.of(remainingText));
            }
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
        GraphicalSquaresAnnotation squaresAnnotation = null;
        GraphicalArrowsAnnotation arrowsAnnotation = null;

        for (Annotation annotation : annotations) {
            if (annotation instanceof SymbolAnnotation) {
                symbolAnnotations.add((SymbolAnnotation) annotation);
            } else if (annotation instanceof TextAfterMoveAnnotation) {
                afterMove = (TextAfterMoveAnnotation) annotation;
            } else if (annotation instanceof TextBeforeMoveAnnotation) {
                beforeMove = (TextBeforeMoveAnnotation) annotation;
            } else if (annotation instanceof GraphicalSquaresAnnotation) {
                squaresAnnotation = (GraphicalSquaresAnnotation) annotation;
            } else if (annotation instanceof GraphicalArrowsAnnotation) {
                arrowsAnnotation = (GraphicalArrowsAnnotation) annotation;
            }
        }

        // Convert SymbolAnnotation to NAGAnnotations
        for (SymbolAnnotation symbolAnnotation : symbolAnnotations) {
            annotations.remove(symbolAnnotation);
            convertSymbolAnnotationToNAGs(annotations, symbolAnnotation);
        }

        // Build combined text annotation with graphical annotations encoded
        StringBuilder textAfterBuilder = new StringBuilder();

        // Add graphical squares annotation in PGN format [%csl ...]
        if (squaresAnnotation != null) {
            annotations.remove(squaresAnnotation);
            String squaresText = formatGraphicalSquares(squaresAnnotation);
            if (!squaresText.isEmpty()) {
                textAfterBuilder.append(squaresText);
            }
        }

        // Add graphical arrows annotation in PGN format [%cal ...]
        if (arrowsAnnotation != null) {
            annotations.remove(arrowsAnnotation);
            String arrowsText = formatGraphicalArrows(arrowsAnnotation);
            if (!arrowsText.isEmpty()) {
                if (!textAfterBuilder.isEmpty()) {
                    textAfterBuilder.append(" ");
                }
                textAfterBuilder.append(arrowsText);
            }
        }

        // Add existing text after move
        if (afterMove != null) {
            annotations.remove(afterMove);
            if (!textAfterBuilder.isEmpty() && !afterMove.text().isEmpty()) {
                textAfterBuilder.append(" ");
            }
            textAfterBuilder.append(afterMove.text());
        }

        // Create combined CommentaryAfterMoveAnnotation if we have any content
        if (!textAfterBuilder.isEmpty()) {
            annotations.add(new CommentaryAfterMoveAnnotation(textAfterBuilder.toString()));
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

    // ========== Graphical Annotation Encoding/Decoding ==========

    /**
     * Formats a GraphicalSquaresAnnotation into PGN square bracket notation.
     * Format: [%csl Ga4,Ya5,Rc6] where G=Green, R=Red, Y=Yellow
     *
     * @param annotation the graphical squares annotation
     * @return the formatted string, or empty string if no squares
     */
    private static String formatGraphicalSquares(@NotNull GraphicalSquaresAnnotation annotation) {
        if (annotation.squares().isEmpty()) {
            return "";
        }

        String squaresStr = annotation.squares().stream()
                .map(square -> colorToChar(square.color()) + Chess.sqiToStr(square.sqi()))
                .collect(Collectors.joining(","));

        return "[%csl " + squaresStr + "]";
    }

    /**
     * Formats a GraphicalArrowsAnnotation into PGN square bracket notation.
     * Format: [%cal Ga1h8,Rb3c4] where G=Green, R=Red, Y=Yellow
     *
     * @param annotation the graphical arrows annotation
     * @return the formatted string, or empty string if no arrows
     */
    private static String formatGraphicalArrows(@NotNull GraphicalArrowsAnnotation annotation) {
        if (annotation.arrows().isEmpty()) {
            return "";
        }

        String arrowsStr = annotation.arrows().stream()
                .map(arrow -> colorToChar(arrow.color()) +
                        Chess.sqiToStr(arrow.fromSqi()) +
                        Chess.sqiToStr(arrow.toSqi()))
                .collect(Collectors.joining(","));

        return "[%cal " + arrowsStr + "]";
    }

    /**
     * Converts a GraphicalAnnotationColor to its single-character PGN representation.
     *
     * @param color the color
     * @return single character: G, R, or Y
     */
    private static String colorToChar(@NotNull GraphicalAnnotationColor color) {
        return switch (color) {
            case GREEN -> "G";
            case RED -> "R";
            case YELLOW -> "Y";
            default -> "G"; // Default to green for NONE or NOT_USED
        };
    }

    /**
     * Converts a single-character PGN color to GraphicalAnnotationColor.
     *
     * @param ch the character (G, R, or Y)
     * @return the color, or null if invalid
     */
    private static GraphicalAnnotationColor charToColor(char ch) {
        return switch (ch) {
            case 'G', 'g' -> GraphicalAnnotationColor.GREEN;
            case 'R', 'r' -> GraphicalAnnotationColor.RED;
            case 'Y', 'y' -> GraphicalAnnotationColor.YELLOW;
            default -> null;
        };
    }

    // Regex patterns for parsing graphical annotations from text
    private static final Pattern SQUARES_PATTERN = Pattern.compile("\\[%csl\\s+([^\\]]+)\\]");
    private static final Pattern ARROWS_PATTERN = Pattern.compile("\\[%cal\\s+([^\\]]+)\\]");

    /**
     * Parses graphical annotations from commentary text and adds them to the annotations collection.
     * Removes the square bracket notation from the text.
     *
     * @param annotations the annotations collection to add graphical annotations to
     * @param commentary the commentary text that may contain square bracket notation
     * @return the commentary text with square bracket notation removed
     */
    private static String parseAndExtractGraphicalAnnotations(@NotNull Annotations annotations, @NotNull String commentary) {
        String remainingText = commentary;

        // Parse and extract squares annotation [%csl ...]
        Matcher squaresMatcher = SQUARES_PATTERN.matcher(remainingText);
        if (squaresMatcher.find()) {
            String squaresData = squaresMatcher.group(1);
            GraphicalSquaresAnnotation squaresAnnotation = parseGraphicalSquares(squaresData);
            if (squaresAnnotation != null) {
                annotations.add(squaresAnnotation);
            }
            remainingText = squaresMatcher.replaceFirst("").trim();
        }

        // Parse and extract arrows annotation [%cal ...]
        Matcher arrowsMatcher = ARROWS_PATTERN.matcher(remainingText);
        if (arrowsMatcher.find()) {
            String arrowsData = arrowsMatcher.group(1);
            GraphicalArrowsAnnotation arrowsAnnotation = parseGraphicalArrows(arrowsData);
            if (arrowsAnnotation != null) {
                annotations.add(arrowsAnnotation);
            }
            remainingText = arrowsMatcher.replaceFirst("").trim();
        }

        return remainingText.trim();
    }

    /**
     * Parses a graphical squares annotation from the data inside [%csl ...].
     * Format: Ga4,Bb5,Rc6 (color + square, comma-separated)
     *
     * @param data the data string (e.g., "Ga4,Bb5,Rc6")
     * @return the GraphicalSquaresAnnotation, or null if invalid
     */
    private static GraphicalSquaresAnnotation parseGraphicalSquares(@NotNull String data) {
        List<GraphicalSquaresAnnotation.Square> squares = new ArrayList<>();

        for (String item : data.split(",")) {
            item = item.trim();
            if (item.length() < 3) {
                log.warn("Invalid square annotation format: {}", item);
                continue;
            }

            GraphicalAnnotationColor color = charToColor(item.charAt(0));
            if (color == null) {
                log.warn("Invalid color in square annotation: {}", item.charAt(0));
                continue;
            }

            String squareStr = item.substring(1);
            int sqi = Chess.strToSqi(squareStr);
            if (sqi == -1) {
                log.warn("Invalid square in square annotation: {}", squareStr);
                continue;
            }

            squares.add(ImmutableSquare.of(color, sqi));
        }

        if (squares.isEmpty()) {
            return null;
        }

        return ImmutableGraphicalSquaresAnnotation.of(squares);
    }

    /**
     * Parses a graphical arrows annotation from the data inside [%cal ...].
     * Format: Ga1h8,Rb3c4 (color + from-square + to-square, comma-separated)
     *
     * @param data the data string (e.g., "Ga1h8,Rb3c4")
     * @return the GraphicalArrowsAnnotation, or null if invalid
     */
    private static GraphicalArrowsAnnotation parseGraphicalArrows(@NotNull String data) {
        List<GraphicalArrowsAnnotation.Arrow> arrows = new ArrayList<>();

        for (String item : data.split(",")) {
            item = item.trim();
            if (item.length() < 5) {
                log.warn("Invalid arrow annotation format: {}", item);
                continue;
            }

            GraphicalAnnotationColor color = charToColor(item.charAt(0));
            if (color == null) {
                log.warn("Invalid color in arrow annotation: {}", item.charAt(0));
                continue;
            }

            String squaresStr = item.substring(1);
            if (squaresStr.length() != 4) {
                log.warn("Invalid arrow squares format: {}", squaresStr);
                continue;
            }

            String fromStr = squaresStr.substring(0, 2);
            String toStr = squaresStr.substring(2, 4);

            int fromSqi = Chess.strToSqi(fromStr);
            int toSqi = Chess.strToSqi(toStr);

            if (fromSqi == -1 || toSqi == -1) {
                log.warn("Invalid squares in arrow annotation: {} -> {}", fromStr, toStr);
                continue;
            }

            arrows.add(ImmutableArrow.of(color, fromSqi, toSqi));
        }

        if (arrows.isEmpty()) {
            return null;
        }

        return ImmutableGraphicalArrowsAnnotation.of(arrows);
    }
}
