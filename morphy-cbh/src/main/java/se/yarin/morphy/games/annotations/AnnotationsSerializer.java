package se.yarin.morphy.games.annotations;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.DatabaseContext;
import se.yarin.morphy.exceptions.MorphyAnnotationExecption;
import se.yarin.morphy.metrics.ItemMetrics;
import se.yarin.morphy.metrics.MetricsRef;
import se.yarin.util.ByteBufferUtil;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.annotations.Annotation;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.*;

public final class AnnotationsSerializer {

    private static final Logger log = LoggerFactory.getLogger(AnnotationsSerializer.class);

    private final @NotNull DatabaseContext context;
    private final @NotNull MetricsRef<ItemMetrics> itemMetricsRef;

    public AnnotationsSerializer() {
        this(new DatabaseContext());
    }

    public AnnotationsSerializer(@NotNull DatabaseContext context) {
        this.context = context;
        this.itemMetricsRef = ItemMetrics.register(context.instrumentation(), "MoveAnnotations");
    }

    private static Map<Integer, AnnotationSerializer> annotationSerializers = new HashMap<>();
    private static Map<Class, AnnotationSerializer> annotationSerializersByClass = new HashMap<>();

    static {
        Arrays.asList(
            new ImmutableBlackClockAnnotation.Serializer(),
            new ImmutableSymbolAnnotation.Serializer(),
            new ImmutableComputerEvaluationAnnotation.Serializer(),
            new ImmutableCorrespondenceMoveAnnotation.Serializer(),
            new ImmutableCriticalPositionAnnotation.Serializer(),
            new GameQuotationAnnotation.Serializer(),
            new ImmutableGraphicalArrowsAnnotation.Serializer(),
            new ImmutableGraphicalSquaresAnnotation.Serializer(),
            new ImmutableMedalAnnotation.Serializer(),
            new ImmutablePawnStructureAnnotation.Serializer(),
            new ImmutablePictureAnnotation.Serializer(),
            new ImmutablePiecePathAnnotation.Serializer(),
            new ImmutableSoundAnnotation.Serializer(),
            new ImmutableTextAfterMoveAnnotation.Serializer(),
            new ImmutableTextBeforeMoveAnnotation.Serializer(),
            new ImmutableTimeControlAnnotation.Serializer(),
            new ImmutableTimeSpentAnnotation.Serializer(),
            new ImmutableTrainingAnnotation.Serializer(),
            new ImmutableVariationColorAnnotation.Serializer(),
            new ImmutableVideoAnnotation.Serializer(),
            new ImmutableVideoStreamTimeAnnotation.Serializer(),
            new ImmutableWebLinkAnnotation.Serializer(),
            new ImmutableWhiteClockAnnotation.Serializer()
        ).forEach(AnnotationsSerializer::registerAnnotationSerializer);
    }

    private static void registerAnnotationSerializer(AnnotationSerializer serializer) {
        annotationSerializers.put(serializer.getAnnotationType(), serializer);
        annotationSerializersByClass.put(serializer.getAnnotationClass(), serializer);
    }

    /**
     * Serializes the annotations in a {@link GameMovesModel}
     * @param gameId the id of the game
     * @param model the game model
     * @return a buffer containing the serialized annotations
     */
    public @NotNull ByteBuffer serializeAnnotations(int gameId, @NotNull GameMovesModel model) {
        itemMetricsRef.update(metrics -> metrics.addSerialization(1));

        // TODO: Double size on demand
        ByteBuffer buf = ByteBuffer.allocate(16384*2);
        ByteBufferUtil.put24BitB(buf, gameId);
        ByteBufferUtil.putIntB(buf, 0x01000E0E);
        ByteBufferUtil.put24BitB(buf, 0); // noAnnotations + 1 (filled in later)
        ByteBufferUtil.putIntB(buf, 0); // Total size (filled in later)

        int posNo = -1, noAnnotations = 0;
        for (GameMovesModel.Node node : model.getAllNodes()) {
            for (Annotation annotation : node.getAnnotations()) {
                AnnotationSerializer serializer = annotationSerializersByClass.get(annotation.getClass());
                if (serializer == null) {
                    log.warn("No annotation serializer found for annotation class " + annotation.getClass().getSimpleName());
                    continue;
                }
                int annotationStart = buf.position();
                ByteBufferUtil.put24BitB(buf, posNo);

                try {
                    serializeAnnotation(annotation, buf);
                    noAnnotations++;
                } catch (IllegalArgumentException e) {
                    // Undo the posNo serialization if the annotation failed to serialize
                    buf.position(annotationStart);
                }
            }
            posNo++;
        }

        buf.flip();
        buf.position(7);
        ByteBufferUtil.put24BitB(buf, noAnnotations + 1);
        ByteBufferUtil.putIntB(buf, buf.limit());
        buf.position(0);

        return buf;
    }

    public void deserializeAnnotations(@NotNull ByteBuffer buf, @NotNull GameMovesModel model) {
        itemMetricsRef.update(metrics -> metrics.addDeserialization(1));

        if (!buf.hasRemaining()) {
            return;
        }
        int gameId = ByteBufferUtil.getUnsigned24BitB(buf);
        int unknown = ByteBufferUtil.getIntB(buf);
        if (unknown != 0x01000E0E) {
            // The 0E is probably the length of the annotation header
            log.warn(String.format("Unknown bytes in annotation header for game " + gameId + ": %08X", unknown));
        }
        int noAnnotations = ByteBufferUtil.getUnsigned24BitB(buf) - 1;
        int size = ByteBufferUtil.getIntB(buf);
        if (log.isDebugEnabled()) {
            log.debug("Parsing " + noAnnotations + " annotations for game " + gameId + " occupying " + size + " bytes");
        }
        List<GameMovesModel.Node> allNodes = model.getAllNodes();
        int expectedEnd = buf.position() - 14 + size;
        for (int i = 0; i < noAnnotations; i++) {
            int posNo = ByteBufferUtil.getSigned24BitB(buf) + 1;
            Annotation annotation = deserializeAnnotation(buf);
            if (posNo < 0 || posNo >= allNodes.size()) {
                log.warn("Invalid position for an annotation in game " + gameId + ": " + (posNo - 1));
            } else {
                allNodes.get(posNo).getAnnotations().add(annotation);
            }
        }
        if (buf.position() != expectedEnd) {
            log.warn("Annotation parser ended at position " + buf.position() + " but expected to end at " + expectedEnd);
        }
    }

    /**
     * Serializes a ChessBase annotation into a {@link ByteBuffer}.
     * @param annotation the annotation to serialize
     * @param buf the buffer to serialize to
     * @throws IllegalArgumentException if no serializer has been registered for the annotation class
     */
    public static void serializeAnnotation(@NotNull Annotation annotation, ByteBuffer buf) {
        if (annotation instanceof RawAnnotation) {
            RawAnnotation ra = (RawAnnotation) annotation;
            ByteBufferUtil.putByte(buf, ra.annotationType());
            ByteBufferUtil.putShortB(buf, ra.rawData().length + 6);
            buf.put(ra.rawData());
        } else {
            AnnotationSerializer serializer = annotationSerializersByClass.get(annotation.getClass());
            if (serializer == null) {
                String msg = "No annotation serializer found for annotation class " + annotation.getClass().getSimpleName();
                throw new IllegalArgumentException(msg);
            }

            int annotationStart = buf.position();

            ByteBufferUtil.putByte(buf, serializer.getAnnotationType());
            ByteBufferUtil.putShortB(buf, 0); // This is the size which we're filling in below

            serializer.serialize(buf, annotation);

            int annotationEnd = buf.position();
            buf.position(annotationStart + 1);
            // The size also includes the position number which we've serialized already
            ByteBufferUtil.putShortB(buf, annotationEnd - annotationStart + 3);
            buf.position(annotationEnd);
        }
    }

    /**
     * Deserializes a ChessBase annotation from a {@link ByteBuffer}.
     * If an annotation is of an unknown type, {@link UnknownAnnotation} will be returned.
     * If there was some error parsing the annotation data, {@link InvalidAnnotation} will be returned.
     * @param buf the byte buffer
     * @return an annotation
     */
    public static Annotation deserializeAnnotation(@NotNull ByteBuffer buf) {
        int startPos = buf.position();
        int annotationType = ByteBufferUtil.getUnsignedByte(buf);
        int annotationSize = ByteBufferUtil.getSignedShortB(buf) - 6;
        int nextPosition = Math.min(buf.limit(), buf.position() + annotationSize);

        try {
            AnnotationSerializer serializer = annotationSerializers.get(annotationType);
            if (serializer != null) {
                return serializer.deserialize(buf, annotationSize);
            } else {
                /*
                Annotation type 0x08 and 0x1A occurs in Megabase 2016.
                They are not visible in the GUI and it's hard to interpret what they mean.
                */
//                log.warn(String.format("Unknown annotation type %d containing %d bytes of data",                            annotationType, noBytes - 6));
                byte[] unknownData = new byte[annotationSize];
                buf.get(unknownData);
                return ImmutableUnknownAnnotation.of(annotationType, unknownData);
            }
        } catch (MorphyAnnotationExecption | IllegalArgumentException | BufferUnderflowException e) {
            buf.position(startPos);
            byte[] invalidData = new byte[annotationSize];
            buf.get(invalidData);
            return ImmutableInvalidAnnotation.of(annotationType, invalidData);
        } finally {
            buf.position(nextPosition);
        }
    }
}
