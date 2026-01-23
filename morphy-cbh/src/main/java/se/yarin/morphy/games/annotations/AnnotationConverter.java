package se.yarin.morphy.games.annotations;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.Chess;
import se.yarin.chess.NAG;
import se.yarin.chess.NAGType;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.annotations.Annotations;
import se.yarin.chess.annotations.CommentaryAfterMoveAnnotation;
import se.yarin.chess.annotations.CommentaryBeforeMoveAnnotation;
import se.yarin.chess.annotations.NAGAnnotation;
import se.yarin.morphy.entities.Nation;

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
 *   <li>Use {@link #convertToStorageAnnotations(Annotations)} as a method reference in
 *       {@link se.yarin.chess.annotations.AnnotationTransformer} when parsing PGN or saving to database</li>
 *   <li>Use {@link #convertToGenericAnnotations(Annotations)} as a method reference in
 *       {@link se.yarin.chess.annotations.AnnotationTransformer} when exporting to PGN or loading from database</li>
 * </ul>
 *
 * <p>Example with PgnParser and PgnExporter:
 * <pre>{@code
 * PgnParser parser = new PgnParser(AnnotationConverter::convertNodeToStorageAnnotations);
 * PgnExporter exporter = new PgnExporter(options, AnnotationConverter::convertNodeToGenericAnnotations);
 * }</pre>
 */
public class AnnotationConverter {
    private static final Logger log = LoggerFactory.getLogger(AnnotationConverter.class);

    public static void trimAnnotations(@NotNull Annotations annotations) {
        // Mainly for testing round trips
        Annotations newAnnotations = new Annotations();

        for (Annotation annotation : annotations) {
            if (annotation instanceof TextAfterMoveAnnotation a) {
                String trim = a.text().trim();
                if (trim.isEmpty()) continue;
                annotation = ImmutableTextAfterMoveAnnotation.builder().from(a).text(trim).build();
            }
            if (annotation instanceof TextBeforeMoveAnnotation a) {
                String trim = a.text().trim();
                if (trim.isEmpty()) continue;
                annotation = ImmutableTextBeforeMoveAnnotation.builder().from(a).text(trim).build();
            }
            if (annotation instanceof CommentaryAfterMoveAnnotation a) {
                String trim = a.getCommentary().trim();
                if (trim.isEmpty()) continue;
                annotation = new CommentaryAfterMoveAnnotation(trim);
            }
            if (annotation instanceof CommentaryBeforeMoveAnnotation a) {
                String trim = a.getCommentary().trim();
                if (trim.isEmpty()) continue;
                annotation = new CommentaryBeforeMoveAnnotation(trim);
            }
            newAnnotations.add(annotation);
        }

        annotations.clear();
        annotations.addAll(newAnnotations);
    }

    /**
     * Converts annotations from generic to storage format.
     *
     * @param annotations the annotations collection to convert
     */
    public static void convertToStorageAnnotations(@NotNull Annotations annotations) {
        if (annotations.isEmpty()) {
            return;
        }
        Annotations originalAnnotations = new Annotations(annotations);
        annotations.clear();

        // Collect generic annotations that need conversion
        List<NAGAnnotation> nagAnnotations = new ArrayList<>();
        List<CommentaryAfterMoveAnnotation> afterMoveAnnotations = new ArrayList<>();
        List<CommentaryBeforeMoveAnnotation> beforeMoveAnnotations = new ArrayList<>();

        for (Annotation annotation : originalAnnotations) {
            if (annotation instanceof NAGAnnotation a) {
                nagAnnotations.add(a);
            } else if (annotation instanceof CommentaryAfterMoveAnnotation a) {
                afterMoveAnnotations.add(a);
            } else if (annotation instanceof CommentaryBeforeMoveAnnotation a) {
                beforeMoveAnnotations.add(a);
            } else {
                log.warn("Unsupported annotation type: {}", annotation.getClass().getName());
                annotations.add(annotation);
            }
        }

        // Convert NAG annotations if present
        if (!nagAnnotations.isEmpty()) {
            annotations.addAll(convertNAGAnnotationsToStorage(nagAnnotations));
        }

        // Convert commentary after move, parsing out any graphical annotations
        if (!afterMoveAnnotations.isEmpty()) {
            // Parse and extract graphical annotations from the commentary text
            ArrayList<String> remaining = new ArrayList<>();
            for (CommentaryAfterMoveAnnotation afterMove : afterMoveAnnotations) {
                String s = parseAndExtractDecodedAnnotations(annotations, afterMove.getCommentary(), false).strip();
                if (!s.isEmpty()) {
                    remaining.add(s);
                }
            }

            // Only create TextAfterMoveAnnotation if there's remaining text
            if (!remaining.isEmpty()) {
                annotations.add(ImmutableTextAfterMoveAnnotation.of(String.join(" ", remaining)));
            }
        }

        // Convert commentary before move, parsing out any graphical annotations
        if (!beforeMoveAnnotations.isEmpty()) {
            // Parse and extract graphical annotations from the commentary text
            ArrayList<String> remaining = new ArrayList<>();
            for (CommentaryBeforeMoveAnnotation beforeMove : beforeMoveAnnotations) {
                String s = parseAndExtractDecodedAnnotations(annotations, beforeMove.getCommentary(), true).strip();
                if (!s.isEmpty()) {
                    remaining.add(s);
                }
            }

            // Only create TextBeforeMoveAnnotation if there's remaining text
            if (!remaining.isEmpty()) {
                annotations.add(ImmutableTextBeforeMoveAnnotation.of(String.join(" ", remaining)));
            }
        }
    }

    /**
     * Converts annotations from storage to generic format.
     *
     * @param annotations the annotations collection to convert
     */
    public static void convertToGenericAnnotations(@NotNull Annotations annotations) {
        if (annotations.isEmpty()) {
            return;
        }

        // Collect storage annotations that need conversion
        List<SymbolAnnotation> symbolAnnotations = new ArrayList<>();
        List<TextAfterMoveAnnotation> afterMoveAnnotations = new ArrayList<>();
        List<TextBeforeMoveAnnotation> beforeMoveAnnotations = new ArrayList<>();
        GraphicalSquaresAnnotation squaresAnnotation = null;
        GraphicalArrowsAnnotation arrowsAnnotation = null;

        for (Annotation annotation : annotations) {
            if (annotation instanceof SymbolAnnotation) {
                symbolAnnotations.add((SymbolAnnotation) annotation);
            } else if (annotation instanceof TextAfterMoveAnnotation) {
                afterMoveAnnotations.add((TextAfterMoveAnnotation) annotation);
            } else if (annotation instanceof TextBeforeMoveAnnotation) {
                beforeMoveAnnotations.add((TextBeforeMoveAnnotation) annotation);
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
        for (TextAfterMoveAnnotation afterMove : afterMoveAnnotations) {
            annotations.remove(afterMove);
            if (!textAfterBuilder.isEmpty() && !afterMove.text().isEmpty()) {
                textAfterBuilder.append(" ");
            }
            if (afterMove.language() == Nation.NONE) {
                textAfterBuilder.append(afterMove.text());
            } else {
                textAfterBuilder.append("[%lang ").append(afterMove.language().getIocCode()).append(" ").append(afterMove.text()).append("]");
            }
        }

        // Create combined CommentaryAfterMoveAnnotation if we have any content
        if (!textAfterBuilder.isEmpty()) {
            annotations.add(new CommentaryAfterMoveAnnotation(textAfterBuilder.toString()));
        }

        StringBuilder textBeforeBuilder = new StringBuilder();

        // Convert text before move
        // Always use [%blang LANG text] notation to explicitly mark before-move comments
        // This distinguishes them from after-move comments when in ambiguous positions
        for (TextBeforeMoveAnnotation beforeMove : beforeMoveAnnotations) {
            annotations.remove(beforeMove);
            if (!textBeforeBuilder.isEmpty() && !beforeMove.text().isEmpty()) {
                textBeforeBuilder.append(" ");
            }
            String langCode = beforeMove.language() == Nation.NONE ? "NONE" : beforeMove.language().getIocCode();
            textBeforeBuilder.append("[%blang ").append(langCode).append(" ").append(beforeMove.text()).append("]");
        }

        if (!textBeforeBuilder.isEmpty()) {
            annotations.add(new CommentaryBeforeMoveAnnotation(textBeforeBuilder.toString()));
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
     * @param nagAnnotations the list of NAG annotations to consolidate
     */
    private static List<Annotation> convertNAGAnnotationsToStorage(@NotNull List<NAGAnnotation> nagAnnotations) {
        ArrayList<Annotation> annotations = new ArrayList<>();
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

        // Create consolidated SymbolAnnotation if we have any NAGs
        if (!nagsByType.isEmpty()) {
            NAG moveComment = nagsByType.getOrDefault(NAGType.MOVE_COMMENT, NAG.NONE);
            NAG lineEvaluation = nagsByType.getOrDefault(NAGType.LINE_EVALUATION, NAG.NONE);
            NAG movePrefix = nagsByType.getOrDefault(NAGType.MOVE_PREFIX, NAG.NONE);

            annotations.add(ImmutableSymbolAnnotation.of(moveComment, movePrefix, lineEvaluation));
        }

        return annotations;
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
    // Regex patterns for parsing language specific text annotations
    private static final Pattern LANGUAGE_PATTERN = Pattern.compile("\\[%lang\\s+([A-Z]+)\\s+([^\\]]+)\\]");
    // Regex pattern for parsing before-move language specific text annotations
    private static final Pattern BEFORE_LANGUAGE_PATTERN = Pattern.compile("\\[%blang\\s+([A-Z]+)\\s+([^\\]]+)\\]");

    /**
     * Parses annotations that are decoded into commentary text as [%...] and
     * adds them to the annotations collection. Removes the square bracket notation from the text.
     *
     * @param annotations the annotations collection to add the decoded annotations to
     * @param commentary the commentary text that may contain square bracket notation
     * @param isBeforeMove whether to create TextBeforeMoveAnnotation (true) or TextAfterMoveAnnotation (false)
     * @return the commentary text with square bracket notation removed
     */
    private static String parseAndExtractDecodedAnnotations(@NotNull Annotations annotations, @NotNull String commentary, boolean isBeforeMove) {
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

        // Parse and extract before-move language annotations [%blang LANG text]
        // These explicitly indicate before-move comments regardless of context
        while (true) {
            Matcher blangMatcher = BEFORE_LANGUAGE_PATTERN.matcher(remainingText);
            if (blangMatcher.find()) {
                String langCode = blangMatcher.group(1);
                Nation nation = langCode.equals("NONE") ? Nation.NONE : Nation.fromIOC(langCode);
                String text = blangMatcher.group(2);
                annotations.add(ImmutableTextBeforeMoveAnnotation.builder().text(text).language(nation).build());
                remainingText = blangMatcher.replaceFirst("").trim();
            } else {
                break;
            }
        }

        // Parse and extract regular language annotations [%lang LANG text]
        while (true) {
            Matcher langMatcher = LANGUAGE_PATTERN.matcher(remainingText);
            if (langMatcher.find()) {
                Nation nation = Nation.fromIOC(langMatcher.group(1));
                String text = langMatcher.group(2);
                if (isBeforeMove) {
                    annotations.add(ImmutableTextBeforeMoveAnnotation.builder().text(text).language(nation).build());
                } else {
                    annotations.add(ImmutableTextAfterMoveAnnotation.builder().text(text).language(nation).build());
                }
                remainingText = langMatcher.replaceFirst("").trim();
            } else {
                break;
            }
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
