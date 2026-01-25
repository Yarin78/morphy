package se.yarin.morphy.games.annotations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.annotations.Annotation;

import java.util.regex.Pattern;

/**
 * Interface for encoding and decoding annotations between ChessBase format and PGN text format.
 *
 * <p>Each annotation class that supports PGN encoding/decoding should have a nested class
 * implementing this interface, following the same pattern as the {@link AnnotationSerializer}
 * nested classes for binary I/O.
 *
 * <p>Example:
 * <pre>{@code
 * @Value.Immutable
 * public abstract class SomeAnnotation extends Annotation {
 *     // Domain fields...
 *
 *     public static class Serializer implements AnnotationSerializer {
 *         // Binary I/O...
 *     }
 *
 *     public static class PgnCodec implements AnnotationPgnCodec {
 *         private static final Pattern PATTERN = Pattern.compile("\\[%some\\s+([^\\]]+)\\]");
 *
 *         @Override
 *         public Pattern getPattern() {
 *             return PATTERN;
 *         }
 *
 *         @Override
 *         public String encode(Annotation annotation) {
 *             SomeAnnotation a = (SomeAnnotation) annotation;
 *             return "[%some " + a.value() + "]";
 *         }
 *
 *         @Override
 *         public Annotation decode(String data) {
 *             return ImmutableSomeAnnotation.of(data.trim());
 *         }
 *
 *         @Override
 *         public Class<? extends Annotation> getAnnotationClass() {
 *             return SomeAnnotation.class;
 *         }
 *     }
 * }
 * }</pre>
 */
public interface AnnotationPgnCodec {

    /**
     * Returns the regex pattern used to match this annotation in PGN commentary text.
     * The pattern should include a capturing group for the annotation data (without the [%...] wrapper).
     *
     * @return the regex pattern for matching this annotation
     */
    @NotNull
    Pattern getPattern();

    /**
     * Encodes a ChessBase annotation to PGN text format (e.g., "[%eval +0.50/20]").
     *
     * @param annotation the annotation to encode (will be of the type returned by {@link #getAnnotationClass()})
     * @return the PGN text representation, or null/empty string if the annotation should be skipped
     */
    @Nullable
    String encode(@NotNull Annotation annotation);

    /**
     * Decodes annotation data from PGN text format.
     * The data parameter contains only the content inside [%...], not the brackets themselves.
     *
     * @param data the annotation data extracted by the pattern (group 1)
     * @return the decoded annotation, or null if parsing fails
     */
    @Nullable
    Annotation decode(@NotNull String data);

    /**
     * Returns the annotation class this codec handles.
     *
     * @return the annotation class
     */
    @NotNull
    Class<? extends Annotation> getAnnotationClass();
}
