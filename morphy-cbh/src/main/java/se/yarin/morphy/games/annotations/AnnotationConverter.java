package se.yarin.morphy.games.annotations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.*;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.annotations.Annotations;
import se.yarin.chess.annotations.CommentaryAfterMoveAnnotation;
import se.yarin.chess.annotations.CommentaryBeforeMoveAnnotation;
import se.yarin.chess.annotations.NAGAnnotation;
import se.yarin.chess.pgn.PgnFormatException;
import se.yarin.chess.pgn.PgnMoveParser;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.games.Medal;

import java.util.*;
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
 *       <li>{@link CommentaryAfterMoveAnnotation} → Various storage annotations + {@link TextAfterMoveAnnotation}</li>
 *       <li>{@link CommentaryBeforeMoveAnnotation} → Various storage annotations + {@link TextBeforeMoveAnnotation}</li>
 *     </ul>
 *   </li>
 *   <li><b>To Generic (for PGN export/display):</b>
 *     <ul>
 *       <li>{@link SymbolAnnotation} → multiple {@link NAGAnnotation} (one per NAG type)</li>
 *       <li>All storage annotations → {@link CommentaryAfterMoveAnnotation} with [%...] encoding</li>
 *       <li>{@link TextBeforeMoveAnnotation} → {@link CommentaryBeforeMoveAnnotation} with [%pre] encoding</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>Usage with PgnParser and PgnExporter:
 * <pre>{@code
 * PgnParser parser = new PgnParser(AnnotationConverter::convertToStorageAnnotations);
 * PgnExporter exporter = new PgnExporter(options, AnnotationConverter::convertToGenericAnnotations);
 * }</pre>
 */
public class AnnotationConverter {
    private static final Logger log = LoggerFactory.getLogger(AnnotationConverter.class);

    // ========== Regex patterns for parsing [%...] annotations ==========

    private static final Pattern SQUARES_PATTERN = Pattern.compile("\\[%csl\\s+([^\\]]+)\\]");
    private static final Pattern ARROWS_PATTERN = Pattern.compile("\\[%cal\\s+([^\\]]+)\\]");
    private static final Pattern CLK_PATTERN = Pattern.compile("\\[%clk\\s+([^\\]]+)\\]");
    private static final Pattern CLKW_PATTERN = Pattern.compile("\\[%clkw\\s+([^\\]]+)\\]");
    private static final Pattern CLKB_PATTERN = Pattern.compile("\\[%clkb\\s+([^\\]]+)\\]");
    private static final Pattern EVAL_PATTERN = Pattern.compile("\\[%eval\\s+([^\\]]+)\\]");
    private static final Pattern EMT_PATTERN = Pattern.compile("\\[%emt\\s+([^\\]]+)\\]");
    private static final Pattern TC_PATTERN = Pattern.compile("\\[%tc\\s+([^\\]]+)\\]");
    private static final Pattern CRIT_PATTERN = Pattern.compile("\\[%crit\\s+([^\\]]+)\\]");
    private static final Pattern MEDAL_PATTERN = Pattern.compile("\\[%medal\\s+([^\\]]+)\\]");
    private static final Pattern VARCOLOR_PATTERN = Pattern.compile("\\[%varcolor\\s+([^\\]]+)\\]");
    private static final Pattern PATH_PATTERN = Pattern.compile("\\[%path\\s+([^\\]]+)\\]");
    private static final Pattern PAWNSTRUCT_PATTERN = Pattern.compile("\\[%pawnstruct\\s+([^\\]]+)\\]");
    private static final Pattern VST_PATTERN = Pattern.compile("\\[%vst\\s+([^\\]]+)\\]");
    private static final Pattern WEBLINK_PATTERN = Pattern.compile("\\[%weblink\\s+(\"(?:[^\"\\\\]|\\\\.)*\")\\s+(\"(?:[^\"\\\\]|\\\\.)*\")\\]");
    private static final Pattern TRAIN_PATTERN = Pattern.compile("\\[%train\\s+([A-Za-z0-9+/=]+)\\]");
    private static final Pattern CORR_PATTERN = Pattern.compile("\\[%corr\\s+([A-Za-z0-9+/=]+)\\]");
    private static final Pattern SOUND_PATTERN = Pattern.compile("\\[%sound\\s+([A-Za-z0-9+/=]+)\\]");
    private static final Pattern VIDEO_PATTERN = Pattern.compile("\\[%video\\s+([A-Za-z0-9+/=]+)\\]");
    private static final Pattern PICTURE_PATTERN = Pattern.compile("\\[%picture\\s+([A-Za-z0-9+/=]+)\\]");
    private static final Pattern QUOTE_PATTERN = Pattern.compile("\\[%quote\\s+(.+?)\\](?=\\s*(?:\\[%|$|[^\\[]))", Pattern.DOTALL);
    private static final Pattern PRE_LANG_PATTERN = Pattern.compile("\\[%pre:([A-Z]{3})\\s+((?:[^\\]\\\\]|\\\\.)*)\\]");
    private static final Pattern PRE_PATTERN = Pattern.compile("\\[%pre\\s+((?:[^\\]\\\\]|\\\\.)*)\\]");
    private static final Pattern POST_LANG_PATTERN = Pattern.compile("\\[%post:([A-Z]{3})\\s+((?:[^\\]\\\\]|\\\\.)*)\\]");

    // Medal name mappings
    private static final Map<String, Medal> MEDAL_FROM_STRING = new HashMap<>();
    private static final Map<Medal, String> MEDAL_TO_STRING = new HashMap<>();
    static {
        MEDAL_FROM_STRING.put("best", Medal.BEST_GAME);
        MEDAL_FROM_STRING.put("decided", Medal.DECIDED_TOURNAMENT);
        MEDAL_FROM_STRING.put("model", Medal.MODEL_GAME);
        MEDAL_FROM_STRING.put("novelty", Medal.NOVELTY);
        MEDAL_FROM_STRING.put("pawn", Medal.PAWN_STRUCTURE);
        MEDAL_FROM_STRING.put("strategy", Medal.STRATEGY);
        MEDAL_FROM_STRING.put("tactics", Medal.TACTICS);
        MEDAL_FROM_STRING.put("attack", Medal.WITH_ATTACK);
        MEDAL_FROM_STRING.put("sacrifice", Medal.SACRIFICE);
        MEDAL_FROM_STRING.put("defense", Medal.DEFENSE);
        MEDAL_FROM_STRING.put("material", Medal.MATERIAL);
        MEDAL_FROM_STRING.put("piece", Medal.PIECE_PLAY);
        MEDAL_FROM_STRING.put("endgame", Medal.ENDGAME);
        MEDAL_FROM_STRING.put("tactblunder", Medal.TACTICAL_BLUNDER);
        MEDAL_FROM_STRING.put("stratblunder", Medal.STRATEGICAL_BLUNDER);
        MEDAL_FROM_STRING.put("user", Medal.USER);

        for (Map.Entry<String, Medal> entry : MEDAL_FROM_STRING.entrySet()) {
            MEDAL_TO_STRING.put(entry.getValue(), entry.getKey());
        }
    }

    // Critical position type mappings
    private static final Map<String, CriticalPositionAnnotation.CriticalPositionType> CRIT_FROM_STRING = new HashMap<>();
    private static final Map<CriticalPositionAnnotation.CriticalPositionType, String> CRIT_TO_STRING = new HashMap<>();
    static {
        CRIT_FROM_STRING.put("opening", CriticalPositionAnnotation.CriticalPositionType.OPENING);
        CRIT_FROM_STRING.put("middlegame", CriticalPositionAnnotation.CriticalPositionType.MIDDLEGAME);
        CRIT_FROM_STRING.put("endgame", CriticalPositionAnnotation.CriticalPositionType.ENDGAME);

        CRIT_TO_STRING.put(CriticalPositionAnnotation.CriticalPositionType.OPENING, "opening");
        CRIT_TO_STRING.put(CriticalPositionAnnotation.CriticalPositionType.MIDDLEGAME, "middlegame");
        CRIT_TO_STRING.put(CriticalPositionAnnotation.CriticalPositionType.ENDGAME, "endgame");
    }

    // ========== Public conversion methods ==========

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
     * Without player context, %clk annotations cannot be properly decoded (will default to White).
     *
     * @param annotations the annotations collection to convert
     */
    public static void convertToStorageAnnotations(@NotNull Annotations annotations) {
        convertToStorageAnnotations(annotations, null);
    }

    /**
     * Converts annotations from generic to storage format.
     * This method can be used as a method reference for {@link se.yarin.chess.annotations.AnnotationTransformer}.
     *
     * @param annotations the annotations collection to convert
     * @param lastMoveBy the player who made the last move (for %clk decoding), or null if unknown
     */
    public static void convertToStorageAnnotations(@NotNull Annotations annotations, @Nullable Player lastMoveBy) {
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
                // Keep non-generic annotations as-is
                annotations.add(annotation);
            }
        }

        // Convert NAG annotations if present
        if (!nagAnnotations.isEmpty()) {
            annotations.addAll(convertNAGAnnotationsToStorage(nagAnnotations));
        }

        // Convert commentary after move, parsing out any encoded annotations
        if (!afterMoveAnnotations.isEmpty()) {
            ArrayList<String> remaining = new ArrayList<>();
            for (CommentaryAfterMoveAnnotation afterMove : afterMoveAnnotations) {
                String s = parseAndExtractDecodedAnnotations(annotations, afterMove.getCommentary(), false, lastMoveBy).strip();
                if (!s.isEmpty()) {
                    remaining.add(s);
                }
            }

            // Only create TextAfterMoveAnnotation if there's remaining text
            if (!remaining.isEmpty()) {
                annotations.add(ImmutableTextAfterMoveAnnotation.of(String.join(" ", remaining)));
            }
        }

        // Convert commentary before move, parsing out any encoded annotations
        if (!beforeMoveAnnotations.isEmpty()) {
            ArrayList<String> remaining = new ArrayList<>();
            for (CommentaryBeforeMoveAnnotation beforeMove : beforeMoveAnnotations) {
                String s = parseAndExtractDecodedAnnotations(annotations, beforeMove.getCommentary(), true, lastMoveBy).strip();
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
        convertToGenericAnnotations(annotations, null);
    }

    /**
     * Converts annotations from storage to generic format.
     * This method can be used as a method reference for {@link se.yarin.chess.annotations.AnnotationTransformer}.
     *
     * @param annotations the annotations collection to convert
     * @param lastMoveBy the player who made the last move (not used for encoding, but required by interface)
     */
    public static void convertToGenericAnnotations(@NotNull Annotations annotations, @Nullable Player lastMoveBy) {
        if (annotations.isEmpty()) {
            return;
        }

        // Collect storage annotations that need conversion
        List<SymbolAnnotation> symbolAnnotations = new ArrayList<>();
        List<TextAfterMoveAnnotation> afterMoveAnnotations = new ArrayList<>();
        List<TextBeforeMoveAnnotation> beforeMoveAnnotations = new ArrayList<>();

        // Annotations that get encoded into the after-move comment
        List<Annotation> encodedAnnotations = new ArrayList<>();

        for (Annotation annotation : annotations) {
            if (annotation instanceof SymbolAnnotation a) {
                symbolAnnotations.add(a);
            } else if (annotation instanceof TextAfterMoveAnnotation a) {
                afterMoveAnnotations.add(a);
            } else if (annotation instanceof TextBeforeMoveAnnotation a) {
                beforeMoveAnnotations.add(a);
            } else if (isEncodableAnnotation(annotation)) {
                encodedAnnotations.add(annotation);
            }
        }

        // Remove all annotations we're going to convert
        for (SymbolAnnotation a : symbolAnnotations) annotations.remove(a);
        for (TextAfterMoveAnnotation a : afterMoveAnnotations) annotations.remove(a);
        for (TextBeforeMoveAnnotation a : beforeMoveAnnotations) annotations.remove(a);
        for (Annotation a : encodedAnnotations) annotations.remove(a);

        // Convert SymbolAnnotation to NAGAnnotations
        for (SymbolAnnotation symbolAnnotation : symbolAnnotations) {
            convertSymbolAnnotationToNAGs(annotations, symbolAnnotation);
        }

        // Build combined text annotation with all encoded annotations
        StringBuilder textAfterBuilder = new StringBuilder();

        // Encode graphical annotations first (per spec order)
        for (Annotation annotation : encodedAnnotations) {
            if (annotation instanceof GraphicalSquaresAnnotation a) {
                appendWithSpace(textAfterBuilder, formatGraphicalSquares(a));
            } else if (annotation instanceof GraphicalArrowsAnnotation a) {
                appendWithSpace(textAfterBuilder, formatGraphicalArrows(a));
            }
        }

        // Encode clock/time annotations
        for (Annotation annotation : encodedAnnotations) {
            if (annotation instanceof WhiteClockAnnotation a) {
                appendWithSpace(textAfterBuilder, formatClockAnnotation(a.clockTime()));
            } else if (annotation instanceof BlackClockAnnotation a) {
                appendWithSpace(textAfterBuilder, formatClockAnnotation(a.clockTime()));
            } else if (annotation instanceof TimeSpentAnnotation a) {
                appendWithSpace(textAfterBuilder, formatTimeSpentAnnotation(a));
            } else if (annotation instanceof TimeControlAnnotation a) {
                appendWithSpace(textAfterBuilder, formatTimeControlAnnotation(a));
            }
        }

        // Encode computer evaluation
        for (Annotation annotation : encodedAnnotations) {
            if (annotation instanceof ComputerEvaluationAnnotation a) {
                String eval = formatComputerEvaluation(a);
                if (eval != null) {
                    appendWithSpace(textAfterBuilder, eval);
                }
            }
        }

        // Encode other metadata annotations
        for (Annotation annotation : encodedAnnotations) {
            if (annotation instanceof CriticalPositionAnnotation a) {
                appendWithSpace(textAfterBuilder, formatCriticalPosition(a));
            } else if (annotation instanceof MedalAnnotation a) {
                appendWithSpace(textAfterBuilder, formatMedalAnnotation(a));
            } else if (annotation instanceof VariationColorAnnotation a) {
                appendWithSpace(textAfterBuilder, formatVariationColor(a));
            } else if (annotation instanceof PiecePathAnnotation a) {
                appendWithSpace(textAfterBuilder, formatPiecePath(a));
            } else if (annotation instanceof PawnStructureAnnotation a) {
                appendWithSpace(textAfterBuilder, formatPawnStructure(a));
            } else if (annotation instanceof VideoStreamTimeAnnotation a) {
                appendWithSpace(textAfterBuilder, formatVideoStreamTime(a));
            } else if (annotation instanceof WebLinkAnnotation a) {
                appendWithSpace(textAfterBuilder, formatWebLink(a));
            } else if (annotation instanceof GameQuotationAnnotation a) {
                appendWithSpace(textAfterBuilder, formatGameQuotation(a));
            } else if (annotation instanceof TrainingAnnotation a) {
                appendWithSpace(textAfterBuilder, formatBinaryAnnotation("train", a.rawData()));
            } else if (annotation instanceof CorrespondenceMoveAnnotation a) {
                appendWithSpace(textAfterBuilder, formatBinaryAnnotation("corr", a.rawData()));
            } else if (annotation instanceof SoundAnnotation a) {
                appendWithSpace(textAfterBuilder, formatBinaryAnnotation("sound", a.rawData()));
            } else if (annotation instanceof VideoAnnotation a) {
                appendWithSpace(textAfterBuilder, formatBinaryAnnotation("video", a.rawData()));
            } else if (annotation instanceof PictureAnnotation a) {
                appendWithSpace(textAfterBuilder, formatBinaryAnnotation("picture", a.rawData()));
            }
        }

        // Add existing text after move
        for (TextAfterMoveAnnotation afterMove : afterMoveAnnotations) {
            if (afterMove.language() == Nation.NONE) {
                appendWithSpace(textAfterBuilder, afterMove.text());
            } else {
                appendWithSpace(textAfterBuilder, "[%post:" + afterMove.language().getIocCode() + " " + escapeString(afterMove.text()) + "]");
            }
        }

        // Create combined CommentaryAfterMoveAnnotation if we have any content
        if (!textAfterBuilder.isEmpty()) {
            annotations.add(new CommentaryAfterMoveAnnotation(textAfterBuilder.toString()));
        }

        // Convert text before move
        StringBuilder textBeforeBuilder = new StringBuilder();
        for (TextBeforeMoveAnnotation beforeMove : beforeMoveAnnotations) {
            if (beforeMove.language() == Nation.NONE) {
                appendWithSpace(textBeforeBuilder, "[%pre " + escapeString(beforeMove.text()) + "]");
            } else {
                appendWithSpace(textBeforeBuilder, "[%pre:" + beforeMove.language().getIocCode() + " " + escapeString(beforeMove.text()) + "]");
            }
        }

        if (!textBeforeBuilder.isEmpty()) {
            annotations.add(new CommentaryBeforeMoveAnnotation(textBeforeBuilder.toString()));
        }
    }

    // ========== Helper methods ==========

    private static void appendWithSpace(StringBuilder sb, String text) {
        if (text == null || text.isEmpty()) return;
        if (!sb.isEmpty()) {
            sb.append(" ");
        }
        sb.append(text);
    }

    private static boolean isEncodableAnnotation(Annotation annotation) {
        return annotation instanceof GraphicalSquaresAnnotation
                || annotation instanceof GraphicalArrowsAnnotation
                || annotation instanceof WhiteClockAnnotation
                || annotation instanceof BlackClockAnnotation
                || annotation instanceof ComputerEvaluationAnnotation
                || annotation instanceof TimeSpentAnnotation
                || annotation instanceof TimeControlAnnotation
                || annotation instanceof CriticalPositionAnnotation
                || annotation instanceof MedalAnnotation
                || annotation instanceof VariationColorAnnotation
                || annotation instanceof PiecePathAnnotation
                || annotation instanceof PawnStructureAnnotation
                || annotation instanceof WebLinkAnnotation
                || annotation instanceof VideoStreamTimeAnnotation
                || annotation instanceof GameQuotationAnnotation
                || annotation instanceof TrainingAnnotation
                || annotation instanceof CorrespondenceMoveAnnotation
                || annotation instanceof SoundAnnotation
                || annotation instanceof VideoAnnotation
                || annotation instanceof PictureAnnotation;
    }

    // ========== Time formatting ==========

    /**
     * Formats centiseconds as H:MM:SS.
     */
    private static String formatCentisecondsAsTime(int centiseconds) {
        int totalSeconds = centiseconds / 100;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Parses H:MM:SS or M:SS or S format to centiseconds.
     */
    private static int parseTimeToCentiseconds(String time) {
        String[] parts = time.split(":");
        int hours = 0, minutes = 0, seconds = 0;

        if (parts.length == 3) {
            hours = Integer.parseInt(parts[0]);
            minutes = Integer.parseInt(parts[1]);
            seconds = Integer.parseInt(parts[2]);
        } else if (parts.length == 2) {
            minutes = Integer.parseInt(parts[0]);
            seconds = Integer.parseInt(parts[1]);
        } else if (parts.length == 1) {
            seconds = Integer.parseInt(parts[0]);
        }

        return ((hours * 3600) + (minutes * 60) + seconds) * 100;
    }

    // ========== String escaping ==========

    private static String escapeString(String s) {
        // Escape backslashes first, then other special characters
        // Replace curly braces with placeholders since PGN comments don't support escaping them
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("]", "\\]")
                .replace("{", "\\<")  // Use \< as placeholder for {
                .replace("}", "\\>");  // Use \> as placeholder for }
    }

    private static String unescapeString(String s) {
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (char c : s.toCharArray()) {
            if (escaped) {
                // Map escape sequences back to original characters
                if (c == '<') {
                    sb.append('{');
                } else if (c == '>') {
                    sb.append('}');
                } else {
                    sb.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Parses a quoted string, handling escapes.
     */
    private static String parseQuotedString(String quoted) {
        if (quoted.startsWith("\"") && quoted.endsWith("\"")) {
            return unescapeString(quoted.substring(1, quoted.length() - 1));
        }
        return quoted;
    }

    // ========== Encoding methods (Storage → Generic) ==========

    private static String formatClockAnnotation(int clockTime) {
        return "[%clk " + formatCentisecondsAsTime(clockTime) + "]";
    }

    private static String formatComputerEvaluation(ComputerEvaluationAnnotation a) {
        if (a.evalType() == 3) {
            return null; // Skip unknown eval types
        }

        StringBuilder sb = new StringBuilder("[%eval ");
        if (a.evalType() == 1) {
            // Mate distance
            sb.append("#").append(a.eval());
        } else {
            // Centipawns - use Locale.US to ensure period as decimal separator
            double pawns = a.eval() / 100.0;
            if (pawns >= 0) sb.append("+");
            sb.append(String.format(Locale.US, "%.2f", pawns));
        }
        if (a.ply() > 0) {
            sb.append("/").append(a.ply());
        }
        sb.append("]");
        return sb.toString();
    }

    private static String formatTimeSpentAnnotation(TimeSpentAnnotation a) {
        StringBuilder sb = new StringBuilder("[%emt ");
        sb.append(String.format("%d:%02d:%02d", a.hours(), a.minutes(), a.seconds()));
        if (a.unknownByte() != 0) {
            sb.append("|").append(a.unknownByte());
        }
        sb.append("]");
        return sb.toString();
    }

    private static String formatTimeControlAnnotation(TimeControlAnnotation a) {
        StringBuilder sb = new StringBuilder("[%tc ");
        boolean first = true;
        for (TimeControlAnnotation.TimeSerie ts : a.timeSeries()) {
            if (!first) sb.append("+");
            first = false;

            if (ts.increment() > 0) {
                sb.append("(").append(formatTimeControlDuration(ts.start()));
                sb.append("+").append(formatTimeControlDuration(ts.increment())).append(")");
            } else {
                sb.append(formatTimeControlDuration(ts.start()));
            }
            if (ts.moves() > 0 && ts.moves() < 1000) {
                sb.append("/").append(ts.moves());
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static String formatTimeControlDuration(int centiseconds) {
        int seconds = centiseconds / 100;
        int minutes = seconds / 60;
        seconds %= 60;
        if (minutes > 0 && seconds > 0) {
            return minutes + "m" + seconds + "s";
        } else if (minutes > 0) {
            return minutes + "m";
        } else {
            return seconds + "s";
        }
    }

    private static String formatCriticalPosition(CriticalPositionAnnotation a) {
        String typeStr = CRIT_TO_STRING.get(a.type());
        if (typeStr == null) return "";
        return "[%crit " + typeStr + "]";
    }

    private static String formatMedalAnnotation(MedalAnnotation a) {
        if (a.medals().isEmpty()) return "";
        String medals = a.medals().stream()
                .map(MEDAL_TO_STRING::get)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(","));
        if (medals.isEmpty()) return "";
        return "[%medal " + medals + "]";
    }

    private static String formatVariationColor(VariationColorAnnotation a) {
        StringBuilder sb = new StringBuilder("[%varcolor ");
        sb.append(String.format("#%02X%02X%02X", a.red(), a.green(), a.blue()));
        if (a.onlyMoves() || a.onlyMainline()) {
            sb.append(" ");
            if (a.onlyMoves()) sb.append("M");
            if (a.onlyMainline()) sb.append("L");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String formatPiecePath(PiecePathAnnotation a) {
        return "[%path " + Chess.sqiToStr(a.sqi()) + " " + a.type() + "]";
    }

    private static String formatPawnStructure(PawnStructureAnnotation a) {
        return "[%pawnstruct " + a.type() + "]";
    }

    private static String formatVideoStreamTime(VideoStreamTimeAnnotation a) {
        return "[%vst " + a.time() + "]";
    }

    private static String formatWebLink(WebLinkAnnotation a) {
        return "[%weblink \"" + escapeString(a.url()) + "\" \"" + escapeString(a.text()) + "\"]";
    }

    private static String formatBinaryAnnotation(String command, byte[] data) {
        return "[%" + command + " " + Base64.getEncoder().encodeToString(data) + "]";
    }

    private static String formatGameQuotation(GameQuotationAnnotation a) {
        StringBuilder sb = new StringBuilder("[%quote");
        GameHeaderModel h = a.header();

        // Serialize all header fields as key="value" pairs (no curly braces to avoid PGN comment conflicts)
        for (Map.Entry<String, Object> entry : h.getAllFields().entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            if (value == null) {
                continue; // Skip null values
            }

            sb.append(" ");
            sb.append(fieldName);
            sb.append("=\"");
            sb.append(escapeString(serializeHeaderValue(value)));
            sb.append("\"");
        }

        if (a.unknown() != 0) {
            sb.append(" unknown=\"").append(a.unknown()).append("\"");
        }

        // Moves (if present) - add as a special "moves" field
        if (a.hasGame()) {
            sb.append(" moves=\"");
            GameModel game = a.getGameModel();
            GameMovesModel.Node node = game.moves().root();
            boolean firstMove = true;
            while (node.hasMoves()) {
                node = node.mainNode();
                Move move = node.lastMove();
                if (!firstMove) sb.append(" ");
                firstMove = false;

                int ply = node.ply();
                if (ply % 2 == 1) {
                    sb.append((ply + 1) / 2).append(". ");
                }
                sb.append(move.toSAN());
            }
            sb.append("\"");
        }

        sb.append("]");
        return sb.toString();
    }

    /**
     * Serializes a header value to a string representation suitable for text encoding.
     */
    private static String serializeHeaderValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Integer i) {
            return i.toString();
        }
        if (value instanceof se.yarin.chess.Date d) {
            return d.isUnset() ? "" : d.toString();
        }
        if (value instanceof Eco e) {
            return e.isSet() ? e.toString() : "";
        }
        if (value instanceof GameResult r) {
            return r.toString();
        }
        if (value instanceof NAG n) {
            return n.toString();
        }
        // Fallback for any other type
        return value.toString();
    }

    /**
     * Deserializes a header value from string based on the field name and expected type.
     */
    private static Object deserializeHeaderValue(String fieldName, String valueStr) {
        if (valueStr.isEmpty()) {
            return null;
        }

        // Try to determine the type based on field name and parse accordingly
        return switch (fieldName) {
            case "whiteElo", "blackElo", "round", "subRound", "eventCategory", "eventRounds" -> {
                try {
                    yield Integer.parseInt(valueStr);
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            case "date", "eventDate", "eventEndDate", "sourceDate" -> {
                try {
                    yield parsePgnDate(valueStr);
                } catch (Exception e) {
                    yield null;
                }
            }
            case "eco" -> {
                try {
                    yield new Eco(valueStr);
                } catch (Exception e) {
                    yield null;
                }
            }
            case "result" -> parseGameResult(valueStr);
            case "lineEvaluation" -> {
                try {
                    yield NAG.valueOf(valueStr);
                } catch (Exception e) {
                    yield null;
                }
            }
            default -> valueStr; // Default to String
        };
    }

    private static String formatGraphicalSquares(@NotNull GraphicalSquaresAnnotation annotation) {
        if (annotation.squares().isEmpty()) {
            return "";
        }

        String squaresStr = annotation.squares().stream()
                .map(square -> colorToChar(square.color()) + Chess.sqiToStr(square.sqi()))
                .collect(Collectors.joining(","));

        return "[%csl " + squaresStr + "]";
    }

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

    private static String colorToChar(@NotNull GraphicalAnnotationColor color) {
        return switch (color) {
            case GREEN -> "G";
            case RED -> "R";
            case YELLOW -> "Y";
            default -> "G";
        };
    }

    // ========== Decoding methods (Generic → Storage) ==========

    /**
     * Parses annotations that are encoded in commentary text as [%...] and
     * adds them to the annotations collection. Removes the square bracket notation from the text.
     *
     * @param annotations the annotations collection to add the decoded annotations to
     * @param commentary the commentary text that may contain square bracket notation
     * @param isBeforeMove whether to create TextBeforeMoveAnnotation (true) or TextAfterMoveAnnotation (false)
     * @param lastMoveBy the player who made the last move (for %clk decoding), or null if unknown
     * @return the commentary text with square bracket notation removed
     */
    private static String parseAndExtractDecodedAnnotations(
            @NotNull Annotations annotations,
            @NotNull String commentary,
            boolean isBeforeMove,
            @Nullable Player lastMoveBy) {

        String remainingText = commentary;

        // Parse and extract graphical annotations
        remainingText = extractPattern(SQUARES_PATTERN, remainingText, data -> {
            GraphicalSquaresAnnotation a = parseGraphicalSquares(data);
            if (a != null) annotations.add(a);
        });
        remainingText = extractPattern(ARROWS_PATTERN, remainingText, data -> {
            GraphicalArrowsAnnotation a = parseGraphicalArrows(data);
            if (a != null) annotations.add(a);
        });

        // Parse clock annotations
        remainingText = extractPattern(CLK_PATTERN, remainingText, data -> {
            int time = parseTimeToCentiseconds(data.trim());
            // Use context to determine White or Black clock
            if (lastMoveBy == Player.WHITE) {
                annotations.add(ImmutableWhiteClockAnnotation.of(time));
            } else if (lastMoveBy == Player.BLACK) {
                annotations.add(ImmutableBlackClockAnnotation.of(time));
            } else {
                // Fallback: treat as white clock (or could warn)
                log.debug("Clock annotation without context, defaulting to WhiteClock");
                annotations.add(ImmutableWhiteClockAnnotation.of(time));
            }
        });
        remainingText = extractPattern(CLKW_PATTERN, remainingText, data -> {
            int time = parseTimeToCentiseconds(data.trim());
            annotations.add(ImmutableWhiteClockAnnotation.of(time));
        });
        remainingText = extractPattern(CLKB_PATTERN, remainingText, data -> {
            int time = parseTimeToCentiseconds(data.trim());
            annotations.add(ImmutableBlackClockAnnotation.of(time));
        });

        // Parse eval annotation
        remainingText = extractPattern(EVAL_PATTERN, remainingText, data -> {
            ComputerEvaluationAnnotation a = parseComputerEvaluation(data.trim());
            if (a != null) annotations.add(a);
        });

        // Parse time annotations
        remainingText = extractPattern(EMT_PATTERN, remainingText, data -> {
            TimeSpentAnnotation a = parseTimeSpent(data.trim());
            if (a != null) annotations.add(a);
        });
        remainingText = extractPattern(TC_PATTERN, remainingText, data -> {
            TimeControlAnnotation a = parseTimeControl(data.trim());
            if (a != null) annotations.add(a);
        });

        // Parse metadata annotations
        remainingText = extractPattern(CRIT_PATTERN, remainingText, data -> {
            CriticalPositionAnnotation.CriticalPositionType type = CRIT_FROM_STRING.get(data.trim().toLowerCase());
            if (type != null) {
                annotations.add(ImmutableCriticalPositionAnnotation.of(type));
            }
        });
        remainingText = extractPattern(MEDAL_PATTERN, remainingText, data -> {
            MedalAnnotation a = parseMedalAnnotation(data.trim());
            if (a != null) annotations.add(a);
        });
        remainingText = extractPattern(VARCOLOR_PATTERN, remainingText, data -> {
            VariationColorAnnotation a = parseVariationColor(data.trim());
            if (a != null) annotations.add(a);
        });
        remainingText = extractPattern(PATH_PATTERN, remainingText, data -> {
            PiecePathAnnotation a = parsePiecePath(data.trim());
            if (a != null) annotations.add(a);
        });
        remainingText = extractPattern(PAWNSTRUCT_PATTERN, remainingText, data -> {
            try {
                int type = Integer.parseInt(data.trim());
                annotations.add(ImmutablePawnStructureAnnotation.of(type));
            } catch (NumberFormatException e) {
                log.warn("Invalid pawnstruct type: {}", data);
            }
        });
        remainingText = extractPattern(VST_PATTERN, remainingText, data -> {
            try {
                int time = Integer.parseInt(data.trim());
                annotations.add(ImmutableVideoStreamTimeAnnotation.of(time));
            } catch (NumberFormatException e) {
                log.warn("Invalid vst time: {}", data);
            }
        });

        // Parse weblink annotation
        Matcher weblinkMatcher = WEBLINK_PATTERN.matcher(remainingText);
        if (weblinkMatcher.find()) {
            String url = parseQuotedString(weblinkMatcher.group(1));
            String text = parseQuotedString(weblinkMatcher.group(2));
            annotations.add(ImmutableWebLinkAnnotation.of(url, text));
            remainingText = weblinkMatcher.replaceFirst("").trim();
        }

        // Parse quote annotation
        Matcher quoteMatcher = QUOTE_PATTERN.matcher(remainingText);
        if (quoteMatcher.find()) {
            GameQuotationAnnotation a = parseGameQuotation(quoteMatcher.group(1));
            if (a != null) annotations.add(a);
            remainingText = quoteMatcher.replaceFirst("").trim();
        }

        // Parse binary annotations
        remainingText = extractPattern(TRAIN_PATTERN, remainingText, data -> {
            byte[] bytes = Base64.getDecoder().decode(data);
            annotations.add(ImmutableTrainingAnnotation.of(bytes));
        });
        remainingText = extractPattern(CORR_PATTERN, remainingText, data -> {
            byte[] bytes = Base64.getDecoder().decode(data);
            annotations.add(ImmutableCorrespondenceMoveAnnotation.of(bytes));
        });
        remainingText = extractPattern(SOUND_PATTERN, remainingText, data -> {
            byte[] bytes = Base64.getDecoder().decode(data);
            annotations.add(ImmutableSoundAnnotation.of(bytes));
        });
        remainingText = extractPattern(VIDEO_PATTERN, remainingText, data -> {
            byte[] bytes = Base64.getDecoder().decode(data);
            annotations.add(ImmutableVideoAnnotation.of(bytes));
        });
        remainingText = extractPattern(PICTURE_PATTERN, remainingText, data -> {
            byte[] bytes = Base64.getDecoder().decode(data);
            annotations.add(ImmutablePictureAnnotation.of(bytes));
        });

        // Parse text annotations with language
        // New format: [%pre text] and [%pre:LANG text]
        while (true) {
            Matcher preLangMatcher = PRE_LANG_PATTERN.matcher(remainingText);
            if (preLangMatcher.find()) {
                Nation nation = Nation.fromIOC(preLangMatcher.group(1));
                String text = unescapeString(preLangMatcher.group(2));
                annotations.add(ImmutableTextBeforeMoveAnnotation.builder().text(text).language(nation).build());
                remainingText = preLangMatcher.replaceFirst("").trim();
                continue;
            }

            Matcher preMatcher = PRE_PATTERN.matcher(remainingText);
            if (preMatcher.find()) {
                String text = unescapeString(preMatcher.group(1));
                annotations.add(ImmutableTextBeforeMoveAnnotation.of(text));
                remainingText = preMatcher.replaceFirst("").trim();
                continue;
            }
            break;
        }

        // [%post:LANG text] for after-move with specific language
        while (true) {
            Matcher postLangMatcher = POST_LANG_PATTERN.matcher(remainingText);
            if (postLangMatcher.find()) {
                Nation nation = Nation.fromIOC(postLangMatcher.group(1));
                String text = unescapeString(postLangMatcher.group(2));
                annotations.add(ImmutableTextAfterMoveAnnotation.builder().text(text).language(nation).build());
                remainingText = postLangMatcher.replaceFirst("").trim();
            } else {
                break;
            }
        }

        return remainingText.trim();
    }

    @FunctionalInterface
    private interface PatternHandler {
        void handle(String data);
    }

    private static String extractPattern(Pattern pattern, String text, PatternHandler handler) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                handler.handle(matcher.group(1));
            } catch (Exception e) {
                log.warn("Failed to parse {}: {}", pattern.pattern(), e.getMessage());
            }
            return matcher.replaceFirst("").trim();
        }
        return text;
    }

    private static ComputerEvaluationAnnotation parseComputerEvaluation(String data) {
        try {
            // Format: [+/-]N.NN/depth or #N/depth
            String[] parts = data.split("/");
            String evalPart = parts[0];
            int depth = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

            int eval;
            int evalType;

            if (evalPart.startsWith("#")) {
                // Mate distance
                evalType = 1;
                eval = Integer.parseInt(evalPart.substring(1));
            } else {
                // Centipawns
                evalType = 0;
                double pawns = Double.parseDouble(evalPart);
                eval = (int) Math.round(pawns * 100);
            }

            return ImmutableComputerEvaluationAnnotation.of(eval, evalType, depth);
        } catch (Exception e) {
            log.warn("Failed to parse eval: {}", data);
            return null;
        }
    }

    private static TimeSpentAnnotation parseTimeSpent(String data) {
        try {
            // Format: H:MM:SS or H:MM:SS|flag
            String[] mainParts = data.split("\\|");
            String timePart = mainParts[0];
            int unknownByte = mainParts.length > 1 ? Integer.parseInt(mainParts[1]) : 0;

            String[] timeParts = timePart.split(":");
            int hours = 0, minutes = 0, seconds = 0;

            if (timeParts.length == 3) {
                hours = Integer.parseInt(timeParts[0]);
                minutes = Integer.parseInt(timeParts[1]);
                seconds = Integer.parseInt(timeParts[2]);
            } else if (timeParts.length == 2) {
                minutes = Integer.parseInt(timeParts[0]);
                seconds = Integer.parseInt(timeParts[1]);
            } else if (timeParts.length == 1) {
                seconds = Integer.parseInt(timeParts[0]);
            }

            return ImmutableTimeSpentAnnotation.of(hours, minutes, seconds, unknownByte);
        } catch (Exception e) {
            log.warn("Failed to parse emt: {}", data);
            return null;
        }
    }

    private static TimeControlAnnotation parseTimeControl(String data) {
        try {
            // Format: period1+period2+period3 where each period is time/moves or (time+inc)/moves
            List<TimeControlAnnotation.TimeSerie> series = new ArrayList<>();
            String[] periodParts = data.split("\\+(?![^(]*\\))"); // Split on + not inside parentheses

            for (String period : periodParts) {
                period = period.trim();
                int start = 0;
                int increment = 0;
                int moves = 1000; // Default: rest of game
                int type = 0;

                // Check for /moves suffix
                int slashIdx = period.lastIndexOf('/');
                if (slashIdx > 0) {
                    try {
                        moves = Integer.parseInt(period.substring(slashIdx + 1));
                        period = period.substring(0, slashIdx);
                    } catch (NumberFormatException e) {
                        // Not a valid moves count, leave as is
                    }
                }

                // Check for (time+inc) format
                if (period.startsWith("(") && period.endsWith(")")) {
                    period = period.substring(1, period.length() - 1);
                    String[] incParts = period.split("\\+");
                    start = parseTimeControlDuration(incParts[0].trim());
                    if (incParts.length > 1) {
                        increment = parseTimeControlDuration(incParts[1].trim());
                    }
                } else {
                    start = parseTimeControlDuration(period);
                }

                series.add(ImmutableTimeSerie.of(start, increment, moves, type));
            }

            return ImmutableTimeControlAnnotation.of(series);
        } catch (Exception e) {
            log.warn("Failed to parse tc: {}", data);
            return null;
        }
    }

    private static int parseTimeControlDuration(String duration) {
        // Parse formats like "90m", "30s", "15m30s", etc. to centiseconds
        int total = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)([hms])").matcher(duration.toLowerCase());
        while (m.find()) {
            int value = Integer.parseInt(m.group(1));
            switch (m.group(2)) {
                case "h" -> total += value * 3600 * 100;
                case "m" -> total += value * 60 * 100;
                case "s" -> total += value * 100;
            }
        }
        // If no unit markers, assume minutes for backward compatibility
        if (total == 0) {
            try {
                total = Integer.parseInt(duration) * 60 * 100;
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        return total;
    }

    private static MedalAnnotation parseMedalAnnotation(String data) {
        EnumSet<Medal> medals = EnumSet.noneOf(Medal.class);
        for (String medalStr : data.split(",")) {
            Medal medal = MEDAL_FROM_STRING.get(medalStr.trim().toLowerCase());
            if (medal != null) {
                medals.add(medal);
            }
        }
        if (medals.isEmpty()) return null;
        return ImmutableMedalAnnotation.of(medals);
    }

    private static VariationColorAnnotation parseVariationColor(String data) {
        try {
            String[] parts = data.split("\\s+");
            String colorPart = parts[0];

            // Parse #RRGGBB color
            if (!colorPart.startsWith("#") || colorPart.length() != 7) {
                return null;
            }
            int red = Integer.parseInt(colorPart.substring(1, 3), 16);
            int green = Integer.parseInt(colorPart.substring(3, 5), 16);
            int blue = Integer.parseInt(colorPart.substring(5, 7), 16);

            boolean onlyMoves = false;
            boolean onlyMainline = false;

            if (parts.length > 1) {
                String flags = parts[1];
                onlyMoves = flags.contains("M");
                onlyMainline = flags.contains("L");
            }

            return ImmutableVariationColorAnnotation.of(red, green, blue, onlyMoves, onlyMainline);
        } catch (Exception e) {
            log.warn("Failed to parse varcolor: {}", data);
            return null;
        }
    }

    private static PiecePathAnnotation parsePiecePath(String data) {
        try {
            String[] parts = data.split("\\s+");
            int sqi = Chess.strToSqi(parts[0]);
            int type = parts.length > 1 ? Integer.parseInt(parts[1]) : 3;
            if (sqi < 0) return null;
            return ImmutablePiecePathAnnotation.of(type, sqi);
        } catch (Exception e) {
            log.warn("Failed to parse path: {}", data);
            return null;
        }
    }

    /**
     * Parses the new key="value" format: field1="value1" field2="value2" ...
     * This format avoids curly braces which conflict with PGN comment syntax.
     */
    private static GameQuotationAnnotation parseGameQuotation(String data) {
        GameHeaderModel header = new GameHeaderModel();
        Map<String, String> fields = parseKeyValuePairs(data);

        // Extract moves if present
        String movesStr = fields.remove("moves");

        String unknownStr = fields.remove("unknown");
        int unknown = unknownStr != null ? Integer.parseInt(unknownStr) : 0;

        // Set all other fields in the header
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            Object value = deserializeHeaderValue(entry.getKey(), entry.getValue());
            if (value != null) {
                header.setField(entry.getKey(), value);
            }
        }

        // Parse moves if present
        if (movesStr != null && !movesStr.isEmpty()) {
            GameMovesModel moves = new GameMovesModel();
            String[] moveTokens = movesStr.split("\\s+");
            GameMovesModel.Node current = moves.root();

            for (String moveToken : moveTokens) {
                if (moveToken.matches("\\d+\\.+")) continue;
                if (moveToken.isEmpty()) continue;

                try {
                    PgnMoveParser moveParser = new PgnMoveParser(current.position());
                    Move move = moveParser.parseMove(moveToken);
                    current = current.addMove(move);
                } catch (PgnFormatException e) {
                    log.debug("Failed to parse move in quotation: {}", moveToken);
                    break;
                }
            }

            return new GameQuotationAnnotation(new GameModel(header, moves), unknown);
        }

        return new GameQuotationAnnotation(header, unknown);
    }

    /**
     * Parses key="value" pairs from a string.
     * Format: key1="value1" key2="value2" ...
     */
    private static Map<String, String> parseKeyValuePairs(String content) {
        Map<String, String> result = new HashMap<>();
        if (content.isEmpty()) {
            return result;
        }

        int i = 0;
        while (i < content.length()) {
            // Skip whitespace
            while (i < content.length() && Character.isWhitespace(content.charAt(i))) {
                i++;
            }
            if (i >= content.length()) break;

            // Read key (everything until '=')
            StringBuilder key = new StringBuilder();
            while (i < content.length() && content.charAt(i) != '=') {
                key.append(content.charAt(i));
                i++;
            }

            if (i >= content.length() || content.charAt(i) != '=') {
                log.warn("Expected '=' after key in quote annotation");
                break;
            }
            i++; // Skip '='

            // Expect opening quote
            if (i >= content.length() || content.charAt(i) != '"') {
                log.warn("Expected '\"' after '=' in quote annotation");
                break;
            }
            i++; // Skip opening quote

            // Read value (everything until unescaped closing quote)
            StringBuilder value = new StringBuilder();
            boolean escaped = false;
            while (i < content.length()) {
                char c = content.charAt(i);
                if (escaped) {
                    value.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    i++; // Skip closing quote
                    break;
                } else {
                    value.append(c);
                }
                i++;
            }

            result.put(key.toString().trim(), value.toString());
        }

        return result;
    }

    private static GraphicalAnnotationColor charToColor(char ch) {
        return switch (ch) {
            case 'G', 'g' -> GraphicalAnnotationColor.GREEN;
            case 'R', 'r' -> GraphicalAnnotationColor.RED;
            case 'Y', 'y' -> GraphicalAnnotationColor.YELLOW;
            default -> null;
        };
    }

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

    // ========== NAG conversion ==========

    private static void convertSymbolAnnotationToNAGs(@NotNull Annotations annotations, @NotNull SymbolAnnotation symbolAnnotation) {
        if (symbolAnnotation.moveComment() != NAG.NONE) {
            annotations.add(new NAGAnnotation(symbolAnnotation.moveComment()));
        }
        if (symbolAnnotation.lineEvaluation() != NAG.NONE) {
            annotations.add(new NAGAnnotation(symbolAnnotation.lineEvaluation()));
        }
        if (symbolAnnotation.movePrefix() != NAG.NONE) {
            annotations.add(new NAGAnnotation(symbolAnnotation.movePrefix()));
        }
    }

    private static List<Annotation> convertNAGAnnotationsToStorage(@NotNull List<NAGAnnotation> nagAnnotations) {
        ArrayList<Annotation> annotations = new ArrayList<>();
        Map<NAGType, NAG> nagsByType = new EnumMap<>(NAGType.class);

        for (NAGAnnotation nagAnnotation : nagAnnotations) {
            NAG nag = nagAnnotation.getNag();
            NAGType type = nag.getType();

            if (type == NAGType.NONE) {
                log.debug("Skipping NAG with NONE type: {}", nag);
                continue;
            }

            if (nagsByType.containsKey(type)) {
                log.warn("Multiple NAGs of type {} found on same node. Keeping {} and dropping {}. " +
                                "ChessBase format only supports one NAG per type.",
                        type, nagsByType.get(type), nag);
            } else {
                nagsByType.put(type, nag);
            }
        }

        if (!nagsByType.isEmpty()) {
            NAG moveComment = nagsByType.getOrDefault(NAGType.MOVE_COMMENT, NAG.NONE);
            NAG lineEvaluation = nagsByType.getOrDefault(NAGType.LINE_EVALUATION, NAG.NONE);
            NAG movePrefix = nagsByType.getOrDefault(NAGType.MOVE_PREFIX, NAG.NONE);

            annotations.add(ImmutableSymbolAnnotation.of(moveComment, movePrefix, lineEvaluation));
        }

        return annotations;
    }

    /**
     * Parse a PGN date string (YYYY.MM.DD format with ?? for unknown parts).
     */
    private static se.yarin.chess.Date parsePgnDate(String dateStr) {
        String[] parts = dateStr.split("\\.");
        if (parts.length != 3) {
            return null;
        }

        int year = 0, month = 0, day = 0;

        if (!parts[0].equals("????") && !parts[0].isEmpty()) {
            year = Integer.parseInt(parts[0]);
        }
        if (!parts[1].equals("??") && !parts[1].isEmpty()) {
            month = Integer.parseInt(parts[1]);
        }
        if (!parts[2].equals("??") && !parts[2].isEmpty()) {
            day = Integer.parseInt(parts[2]);
        }

        if (year == 0) {
            return null;
        }
        return new se.yarin.chess.Date(year, month, day);
    }

    /**
     * Parse a game result string into a GameResult enum.
     */
    private static GameResult parseGameResult(String resultStr) {
        if (resultStr == null) {
            return GameResult.NOT_FINISHED;
        }
        return switch (resultStr.trim()) {
            case "1-0" -> GameResult.WHITE_WINS;
            case "0-1" -> GameResult.BLACK_WINS;
            case "1/2-1/2", "1/2" -> GameResult.DRAW;
            case "+:-" -> GameResult.WHITE_WINS_ON_FORFEIT;
            case "-:+" -> GameResult.BLACK_WINS_ON_FORFEIT;
            case "=:=" -> GameResult.DRAW_ON_FORFEIT;
            case "0-0" -> GameResult.BOTH_LOST;
            default -> GameResult.NOT_FINISHED;
        };
    }
}
