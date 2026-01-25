package se.yarin.morphy.games.annotations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.chess.annotations.Annotation;

import java.util.*;

/**
 * Registry for PGN annotation codecs.
 * Maintains mappings between annotation classes and their codecs for encoding/decoding.
 *
 * <p>This class can be instantiated to create separate codec registries for different conversion modes.
 * For backward compatibility, a shared instance is available via {@link #getInstance()}.
 */
public class PgnCodecRegistry {

    private static final PgnCodecRegistry SHARED_INSTANCE = new PgnCodecRegistry();

    private final Map<Class<? extends Annotation>, AnnotationPgnCodec> codecsByClass = new HashMap<>();
    private final List<AnnotationPgnCodec> codecs = new ArrayList<>();

    public PgnCodecRegistry() {
        // Public constructor for creating custom registry instances
    }

    /**
     * Returns a shared instance of the registry for backward compatibility.
     * @deprecated Use instance-based AnnotationConverter factory methods instead.
     */
    @Deprecated
    public static PgnCodecRegistry getInstance() {
        return SHARED_INSTANCE;
    }

    /**
     * Registers a codec in the registry.
     * The codec will be added to both the class-based map and the ordered list.
     *
     * @param codec the codec to register
     */
    public void register(@NotNull AnnotationPgnCodec codec) {
        codecsByClass.put(codec.getAnnotationClass(), codec);
        codecs.add(codec);
    }

    /**
     * Returns the codec for a given annotation class, or null if not registered.
     */
    @Nullable
    public AnnotationPgnCodec getCodec(@NotNull Class<? extends Annotation> annotationClass) {
        return codecsByClass.get(annotationClass);
    }

    /**
     * Returns all registered codecs in registration order.
     * This is useful for iterating through all codecs during encoding/decoding.
     */
    @NotNull
    public List<AnnotationPgnCodec> getAllCodecs() {
        return Collections.unmodifiableList(codecs);
    }

    /**
     * Clears all registered codecs.
     * Primarily useful for testing.
     */
    public void clear() {
        codecsByClass.clear();
        codecs.clear();
    }
}
