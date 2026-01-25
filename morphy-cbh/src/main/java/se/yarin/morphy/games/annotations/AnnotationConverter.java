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
import se.yarin.morphy.entities.Nation;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static se.yarin.morphy.games.annotations.AnnotationPgnUtil.appendWithSpace;

/**
 * Bidirectional converter between PGN annotations and ChessBase annotations.
 *
 * <p>This converter handles the translation layer in both directions:
 * <ul>
 *   <li><b>To ChessBase (for database save):</b>
 *     <ul>
 *       <li>{@link NAGAnnotation} → {@link SymbolAnnotation} (with NAG consolidation)</li>
 *       <li>{@link CommentaryAfterMoveAnnotation} → Various ChessBase annotations + {@link TextAfterMoveAnnotation}</li>
 *       <li>{@link CommentaryBeforeMoveAnnotation} → Various ChessBase annotations + {@link TextBeforeMoveAnnotation}</li>
 *     </ul>
 *   </li>
 *   <li><b>To PGN (for PGN export/display):</b>
 *     <ul>
 *       <li>{@link SymbolAnnotation} → multiple {@link NAGAnnotation} (one per NAG type)</li>
 *       <li>All ChessBase annotations → {@link CommentaryAfterMoveAnnotation} with [%...] encoding</li>
 *       <li>{@link TextBeforeMoveAnnotation} → {@link CommentaryBeforeMoveAnnotation} with [%pre] encoding</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>Usage with PgnParser and PgnExporter:
 * <pre>{@code
 * PgnParser parser = new PgnParser(AnnotationConverter::convertToChessBaseAnnotations);
 * PgnExporter exporter = new PgnExporter(options, AnnotationConverter::convertToPgnAnnotations);
 * }</pre>
 */
public class AnnotationConverter {
    private static final Logger log = LoggerFactory.getLogger(AnnotationConverter.class);

    // Static registry for backward compatibility with static methods
    private static final PgnCodecRegistry DEFAULT_REGISTRY = new PgnCodecRegistry();

    private static final AnnotationConverter roundTripConverter;
    private static final AnnotationConverter simplifiedPgnConverter;

    // Instance fields
    private final PgnCodecRegistry codecRegistry;
    private final boolean simplifyTextAnnotations;

    // Static initializer to register all PGN codecs in the default registry
    // Registration order matters for encoding: graphical < clock < eval < metadata < binary
    static {
        registerAllCodecs(DEFAULT_REGISTRY);

        PgnCodecRegistry registry = new PgnCodecRegistry();
        registerAllCodecs(registry);
        roundTripConverter = new AnnotationConverter(false, registry);
        simplifiedPgnConverter = new AnnotationConverter(true, registry);
    }

    /**
     * Registers all standard annotation codecs in the given registry.
     * Registration order matters for encoding: graphical < clock < eval < metadata < binary.
     *
     * @param registry the registry to populate with codecs
     */
    private static void registerAllCodecs(PgnCodecRegistry registry) {
        registry.register(new GraphicalSquaresAnnotation.PgnCodec());
        registry.register(new GraphicalArrowsAnnotation.PgnCodec());
        registry.register(new WhiteClockAnnotation.PgnCodec());
        registry.register(new BlackClockAnnotation.PgnCodec());
        registry.register(new TimeSpentAnnotation.PgnCodec());
        registry.register(new TimeControlAnnotation.PgnCodec());
        registry.register(new ComputerEvaluationAnnotation.PgnCodec());

        registry.register(new CriticalPositionAnnotation.PgnCodec());
        registry.register(new MedalAnnotation.PgnCodec());
        registry.register(new PawnStructureAnnotation.PgnCodec());
        registry.register(new PiecePathAnnotation.PgnCodec());
        registry.register(new VariationColorAnnotation.PgnCodec());
        registry.register(new VideoStreamTimeAnnotation.PgnCodec());
        registry.register(new WebLinkAnnotation.PgnCodec());

        registry.register(new TrainingAnnotation.PgnCodec());
        registry.register(new CorrespondenceMoveAnnotation.PgnCodec());
        registry.register(new SoundAnnotation.PgnCodec());
        registry.register(new VideoAnnotation.PgnCodec());
        registry.register(new PictureAnnotation.PgnCodec());

        registry.register(new GameQuotationAnnotation.PgnCodec());
    }

    /**
     * Private constructor for creating converter instances.
     *
     * @param simplifyTextAnnotations whether to output plain text without language tags
     * @param registry the codec registry to use for encoding/decoding
     */
    private AnnotationConverter(boolean simplifyTextAnnotations, PgnCodecRegistry registry) {
        this.simplifyTextAnnotations = simplifyTextAnnotations;
        this.codecRegistry = registry;
    }

    /**
     * Creates a round-trip converter with full fidelity.
     * Preserves all ChessBase metadata including language tags and position markers.
     * Use this for PGN files that need to be re-imported into ChessBase.
     *
     * @return AnnotationConverter configured for round-trip conversion
     */
    public static AnnotationConverter getRoundTripConverter() {
        return roundTripConverter;
    }

    /**
     * Creates a simplified PGN converter for human-readable output.
     * <ul>
     *   <li>Text annotations output as plain text (no [%pre]/[%post:LANG] tags)</li>
     *   <li>Language information is lost</li>
     *   <li>Graphical annotations ([%csl], [%cal]) still encoded</li>
     *   <li>Clock, eval, and other [%...] annotations still encoded</li>
     * </ul>
     * Use this for PGN files intended for human reading, not round-trip import.
     *
     * @return AnnotationConverter configured for simplified, readable PGN
     */
    public static AnnotationConverter getSimplifiedPgnConverter() {
        return simplifiedPgnConverter;
    }

    // ========== Instance conversion methods ==========

    /**
     * Converts annotations from ChessBase to PGN format (instance method).
     *
     * @param annotations the annotations collection to convert
     */
    public void convertToPgn(@NotNull Annotations annotations) {
        convertToPgn(annotations, null);
    }

    /**
     * Converts annotations from ChessBase to PGN format (instance method).
     * This method can be used as a method reference for {@link se.yarin.chess.annotations.AnnotationTransformer}.
     *
     * @param annotations the annotations collection to convert
     * @param lastMoveBy  the player who made the last move (not used for encoding, but required by interface)
     */
    public void convertToPgn(@NotNull Annotations annotations, @Nullable Player lastMoveBy) {
        if (annotations.isEmpty()) {
            return;
        }

        // Collect ChessBase annotations that need conversion
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
            } else if (codecRegistry.getCodec(annotation.getClass()) != null) {
                encodedAnnotations.add(annotation);
            } else {
                log.warn("Unsupported annotation of type {} will be removed during conversion", annotation.getClass());
            }
        }

        annotations.clear();

        // Convert SymbolAnnotation to NAGAnnotations
        for (SymbolAnnotation symbolAnnotation : symbolAnnotations) {
            annotations.addAll(SymbolAnnotation.NAGConverter.toNAGAnnotations(symbolAnnotation));
        }

        // Build combined text annotation with all encoded annotations
        StringBuilder textAfterBuilder = new StringBuilder();

        // Process annotations in spec order using registry (codecs registered in correct order)
        // Order: graphical < clock < eval < metadata < binary < complex
        for (AnnotationPgnCodec codec : codecRegistry.getAllCodecs()) {
            for (Annotation annotation : encodedAnnotations) {
                if (annotation.getClass().equals(codec.getAnnotationClass())) {
                    String encoded = codec.encode(annotation);
                    if (encoded != null && !encoded.isEmpty()) {
                        appendWithSpace(textAfterBuilder, encoded);
                    }
                }
            }
        }

        // Add existing text after move
        if (simplifyTextAnnotations) {
            // Simplified mode: plain text, all languages concatenated
            for (TextAfterMoveAnnotation afterMove : afterMoveAnnotations) {
                appendWithSpace(textAfterBuilder, afterMove.text());
            }
        } else {
            // Round-trip mode: preserve language tags
            for (TextAfterMoveAnnotation afterMove : afterMoveAnnotations) {
                if (afterMove.language() == Nation.NONE) {
                    appendWithSpace(textAfterBuilder, afterMove.text());
                } else {
                    appendWithSpace(textAfterBuilder, "[%post:" + afterMove.language().getIocCode() + " " + AnnotationPgnUtil.escapeString(afterMove.text()) + "]");
                }
            }
        }

        // Create combined CommentaryAfterMoveAnnotation if we have any content
        if (!textAfterBuilder.isEmpty()) {
            annotations.add(new CommentaryAfterMoveAnnotation(textAfterBuilder.toString()));
        }

        // Convert text before move
        if (!beforeMoveAnnotations.isEmpty()) {
            if (simplifyTextAnnotations) {
                // Simplified mode: plain text in before-move comment
                StringBuilder textBeforeBuilder = new StringBuilder();
                for (TextBeforeMoveAnnotation beforeMove : beforeMoveAnnotations) {
                    appendWithSpace(textBeforeBuilder, beforeMove.text());
                }
                if (!textBeforeBuilder.isEmpty()) {
                    annotations.add(new CommentaryBeforeMoveAnnotation(textBeforeBuilder.toString()));
                }
            } else {
                // Round-trip mode: preserve [%pre] encoding
                StringBuilder textBeforeBuilder = new StringBuilder();
                for (TextBeforeMoveAnnotation beforeMove : beforeMoveAnnotations) {
                    if (beforeMove.language() == Nation.NONE) {
                        appendWithSpace(textBeforeBuilder, "[%pre " + AnnotationPgnUtil.escapeString(beforeMove.text()) + "]");
                    } else {
                        appendWithSpace(textBeforeBuilder, "[%pre:" + beforeMove.language().getIocCode() + " " + AnnotationPgnUtil.escapeString(beforeMove.text()) + "]");
                    }
                }
                if (!textBeforeBuilder.isEmpty()) {
                    annotations.add(new CommentaryBeforeMoveAnnotation(textBeforeBuilder.toString()));
                }
            }
        }
    }

    /**
     * Converts annotations from PGN to ChessBase format (instance method).
     * Without player context, %clk annotations cannot be properly decoded (will default to White).
     *
     * @param annotations the annotations collection to convert
     */
    public void convertToChessBase(@NotNull Annotations annotations) {
        convertToChessBase(annotations, null);
    }

    /**
     * Converts annotations from PGN to ChessBase format (instance method).
     * This method can be used as a method reference for {@link se.yarin.chess.annotations.AnnotationTransformer}.
     *
     * @param annotations the annotations collection to convert
     * @param lastMoveBy  the player who made the last move (for %clk decoding), or null if unknown
     */
    public void convertToChessBase(@NotNull Annotations annotations, @Nullable Player lastMoveBy) {
        if (annotations.isEmpty()) {
            return;
        }
        Annotations originalAnnotations = new Annotations(annotations);
        annotations.clear();

        // Collect PGN annotations that need conversion
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
                // Keep non-PGN annotations as-is
                annotations.add(annotation);
            }
        }

        // Convert NAG annotations if present
        if (!nagAnnotations.isEmpty()) {
            SymbolAnnotation.NAGConverter.fromNAGAnnotations(nagAnnotations).ifPresent(annotations::add);
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
     * Parses annotations that are encoded in commentary text as [%...] and
     * adds them to the annotations collection. Removes the square bracket notation from the text.
     * Instance version that uses this converter's codec registry.
     *
     * @param annotations  the annotations collection to add the decoded annotations to
     * @param commentary   the commentary text that may contain square bracket notation
     * @param isBeforeMove whether to create TextBeforeMoveAnnotation (true) or TextAfterMoveAnnotation (false)
     * @param lastMoveBy   the player who made the last move (for %clk decoding), or null if unknown
     * @return the commentary text with square bracket notation removed
     */
    private String parseAndExtractDecodedAnnotations(
            @NotNull Annotations annotations,
            @NotNull String commentary,
            boolean isBeforeMove,
            @Nullable Player lastMoveBy) {

        String remainingText = commentary;

        // First pass: try to decode using registered codecs
        for (AnnotationPgnCodec codec : codecRegistry.getAllCodecs()) {
            remainingText = extractPattern(codec.getPattern(), remainingText, data -> {
                Annotation decoded = codec.decode(data.trim());
                if (decoded != null) {
                    annotations.add(decoded);
                }
            });
        }

        // Legacy decoding for context-aware clock annotations
        // Generic %clk requires player context to determine White vs Black
        remainingText = extractPattern(CLK_PATTERN, remainingText, data -> {
            int time = AnnotationPgnUtil.parseTimeToCentiseconds(data.trim());
            if (lastMoveBy == Player.WHITE) {
                annotations.add(ImmutableWhiteClockAnnotation.of(time));
            } else if (lastMoveBy == Player.BLACK) {
                annotations.add(ImmutableBlackClockAnnotation.of(time));
            } else {
                log.debug("Clock annotation without context, defaulting to WhiteClock");
                annotations.add(ImmutableWhiteClockAnnotation.of(time));
            }
        });
        remainingText = extractPattern(CLKW_PATTERN, remainingText, data -> {
            int time = AnnotationPgnUtil.parseTimeToCentiseconds(data.trim());
            annotations.add(ImmutableWhiteClockAnnotation.of(time));
        });
        remainingText = extractPattern(CLKB_PATTERN, remainingText, data -> {
            int time = AnnotationPgnUtil.parseTimeToCentiseconds(data.trim());
            annotations.add(ImmutableBlackClockAnnotation.of(time));
        });

        // Parse text annotations with language
        // New format: [%pre text] and [%pre:LANG text]
        while (true) {
            Matcher preLangMatcher = PRE_LANG_PATTERN.matcher(remainingText);
            if (preLangMatcher.find()) {
                Nation nation = Nation.fromIOC(preLangMatcher.group(1));
                String text = AnnotationPgnUtil.unescapeString(preLangMatcher.group(2));
                annotations.add(ImmutableTextBeforeMoveAnnotation.builder().text(text).language(nation).build());
                remainingText = preLangMatcher.replaceFirst("").trim();
                continue;
            }

            Matcher preMatcher = PRE_PATTERN.matcher(remainingText);
            if (preMatcher.find()) {
                String text = AnnotationPgnUtil.unescapeString(preMatcher.group(1));
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
                String text = AnnotationPgnUtil.unescapeString(postLangMatcher.group(2));
                annotations.add(ImmutableTextAfterMoveAnnotation.builder().text(text).language(nation).build());
                remainingText = postLangMatcher.replaceFirst("").trim();
            } else {
                break;
            }
        }

        return remainingText.trim();
    }

    // ========== Regex patterns for parsing [%...] annotations ==========
    // Only patterns for context-aware and text annotations remain here (others are in codec classes)

    private static final Pattern CLK_PATTERN = Pattern.compile("\\[%clk\\s+([^\\]]+)\\]");
    private static final Pattern CLKW_PATTERN = Pattern.compile("\\[%clkw\\s+([^\\]]+)\\]");
    private static final Pattern CLKB_PATTERN = Pattern.compile("\\[%clkb\\s+([^\\]]+)\\]");
    private static final Pattern PRE_LANG_PATTERN = Pattern.compile("\\[%pre:([A-Z]{3})\\s+((?:[^\\]\\\\]|\\\\.)*)\\]");
    private static final Pattern PRE_PATTERN = Pattern.compile("\\[%pre\\s+((?:[^\\]\\\\]|\\\\.)*)\\]");
    private static final Pattern POST_LANG_PATTERN = Pattern.compile("\\[%post:([A-Z]{3})\\s+((?:[^\\]\\\\]|\\\\.)*)\\]");

    // ========== Public static conversion methods (for backward compatibility) ==========

    /**
     * Removes leading and trailing white space in text annotations.
     *
     * @param annotations The annotations to process
     */
    public static void trimAnnotations(@NotNull Annotations annotations) {
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

    // ========== Decoding methods (PGN → ChessBase) ==========

    /**
     * Parses annotations that are encoded in commentary text as [%...] and
     * adds them to the annotations collection. Removes the square bracket notation from the text.
     * Static version that uses the default registry for backward compatibility.
     *
     * @param annotations  the annotations collection to add the decoded annotations to
     * @param commentary   the commentary text that may contain square bracket notation
     * @param isBeforeMove whether to create TextBeforeMoveAnnotation (true) or TextAfterMoveAnnotation (false)
     * @param lastMoveBy   the player who made the last move (for %clk decoding), or null if unknown
     * @return the commentary text with square bracket notation removed
     */
    private static String parseAndExtractDecodedAnnotationsStatic(
            @NotNull Annotations annotations,
            @NotNull String commentary,
            boolean isBeforeMove,
            @Nullable Player lastMoveBy) {

        String remainingText = commentary;

        // First pass: try to decode using registered codecs
        for (AnnotationPgnCodec codec : DEFAULT_REGISTRY.getAllCodecs()) {
            remainingText = extractPattern(codec.getPattern(), remainingText, data -> {
                Annotation decoded = codec.decode(data.trim());
                if (decoded != null) {
                    annotations.add(decoded);
                }
            });
        }

        // Legacy decoding for context-aware clock annotations
        // Generic %clk requires player context to determine White vs Black
        remainingText = extractPattern(CLK_PATTERN, remainingText, data -> {
            int time = AnnotationPgnUtil.parseTimeToCentiseconds(data.trim());
            if (lastMoveBy == Player.WHITE) {
                annotations.add(ImmutableWhiteClockAnnotation.of(time));
            } else if (lastMoveBy == Player.BLACK) {
                annotations.add(ImmutableBlackClockAnnotation.of(time));
            } else {
                log.debug("Clock annotation without context, defaulting to WhiteClock");
                annotations.add(ImmutableWhiteClockAnnotation.of(time));
            }
        });
        remainingText = extractPattern(CLKW_PATTERN, remainingText, data -> {
            int time = AnnotationPgnUtil.parseTimeToCentiseconds(data.trim());
            annotations.add(ImmutableWhiteClockAnnotation.of(time));
        });
        remainingText = extractPattern(CLKB_PATTERN, remainingText, data -> {
            int time = AnnotationPgnUtil.parseTimeToCentiseconds(data.trim());
            annotations.add(ImmutableBlackClockAnnotation.of(time));
        });

        // Parse text annotations with language
        // New format: [%pre text] and [%pre:LANG text]
        while (true) {
            Matcher preLangMatcher = PRE_LANG_PATTERN.matcher(remainingText);
            if (preLangMatcher.find()) {
                Nation nation = Nation.fromIOC(preLangMatcher.group(1));
                String text = AnnotationPgnUtil.unescapeString(preLangMatcher.group(2));
                annotations.add(ImmutableTextBeforeMoveAnnotation.builder().text(text).language(nation).build());
                remainingText = preLangMatcher.replaceFirst("").trim();
                continue;
            }

            Matcher preMatcher = PRE_PATTERN.matcher(remainingText);
            if (preMatcher.find()) {
                String text = AnnotationPgnUtil.unescapeString(preMatcher.group(1));
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
                String text = AnnotationPgnUtil.unescapeString(postLangMatcher.group(2));
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
}
