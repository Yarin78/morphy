package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.annotations.*;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Symbol;
import se.yarin.chess.annotations.*;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class AnnotationParser {

    private static final Logger log = LoggerFactory.getLogger(AnnotationParser.class);

    private AnnotationParser() { }

    public static void parseGameAnnotations(@NonNull ByteBuffer buf, @NonNull GameMovesModel model) {
        List<Annotations> annotations = parseGameAnnotations(buf);
        List<GameMovesModel.Node> allNodes = model.getAllNodes();
        if (annotations.size() > allNodes.size()) {
            log.warn("Annotations set on moves not in the game");
        }
        for (int i = 0; i < annotations.size() && i < allNodes.size(); i++) {
            annotations.get(i).getAll().forEach(allNodes.get(i)::addAnnotation);
        }
    }

    public static List<Annotations> parseGameAnnotations(@NonNull ByteBuffer buf) {
        ArrayList<Annotations> annotations = new ArrayList<>();
        if (!buf.hasRemaining()) {
            return annotations;
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
        int expectedEnd = buf.position() - 14 + size;
        for (int i = 0; i < noAnnotations; i++) {
            int posNo = ByteBufferUtil.getSigned24BitB(buf);
            Annotation annotation = parseAnnotation(buf);
            if (posNo < -1 || posNo > 1000000) {
                log.warn("Invalid position for an annotation in game " + gameId + ": " + posNo);
            } else {
                posNo++;
                while (annotations.size() <= posNo) {
                    annotations.add(new Annotations());
                }
                annotations.get(posNo).add(annotation);
            }
        }
        if (buf.position() != expectedEnd) {
            log.warn("Annotation parser ended at position " + buf.position() + " but expected to end at " + expectedEnd);
        }
        return annotations;
    }

    /**
     * Gets a ChessBase annotation from a {@link ByteBuffer}.
     * If an annotation is of an unknown type, {@link UnknownAnnotation} will be returned.
     * If there was some error parsing the annotation data, {@link InvalidAnnotation} will be returned.
     * @param buf the byte buffer
     * @return an annotation
     */
    public static Annotation parseAnnotation(@NonNull ByteBuffer buf) {
        int startPos = buf.position();
        int annotationType = ByteBufferUtil.getUnsignedByte(buf);
        int annotationSize = ByteBufferUtil.getSignedShortB(buf) - 6;
        int nextPosition = buf.position() + annotationSize;

        try {
            switch (annotationType) {
                case 0x02 : return getCommentaryAfterMoveAnnotation(buf, annotationSize);
                case 0x03 : return getSymbolAnnotation(buf, annotationSize);
                case 0x04 : return GraphicalSquaresAnnotation.deserialize(buf, annotationSize);
                case 0x05 : return GraphicalArrowsAnnotation.deserialize(buf, annotationSize);
                case 0x07 : return TimeSpentAnnotation.deserialize(buf);
                case 0x09 : return TrainingAnnotation.deserialize(buf, annotationSize);
                case 0x10 : return SoundAnnotation.deserialize(buf, annotationSize);
                case 0x11 : return PictureAnnotation.deserialize(buf, annotationSize);
                case 0x13 : return GameQuotationAnnotation.deserialize(buf);
                case 0x14 : return PawnStructureAnnotation.deserialize(buf);
                case 0x15 : return PiecePathAnnotation.deserialize(buf);
                case 0x16 : return WhiteClockAnnotation.deserialize(buf);
                case 0x17 : return BlackClockAnnotation.deserialize(buf);
                case 0x18 : return CriticalPositionAnnotation.deserialize(buf);
                case 0x19 : return CorrespondenceMoveAnnotation.deserialize(buf, annotationSize);
                case 0x1C : return WebLinkAnnotation.deserialize(buf);
                case 0x20 : return VideoAnnotation.deserialize(buf, annotationSize);
                case 0x21 : return ComputerEvaluationAnnotation.deserialize(buf);
                case 0x22 : return MedalAnnotation.deserialize(buf);
                case 0x23 : return VariationColorAnnotation.deserialize(buf);
                case 0x24 : return TimeControlAnnotation.deserialize(buf);
                case 0x25 : return VideoStreamTimeAnnotation.deserialize(buf);
                case 0x82 : return getCommentaryBeforeMoveAnnotation(buf, annotationSize);

                /*
                Annotation type 0x08 and 0x1A occurs in Megabase 2016.
                They are not visible in the GUI and it's hard to interpret what they mean.
                */
                default :
//                    log.warn(String.format("Unknown annotation type %d containing %d bytes of data",                            annotationType, noBytes - 6));
                    return new UnknownAnnotation(annotationType, buf, annotationSize);
            }
        } catch (ChessBaseAnnotationException | IllegalArgumentException | BufferUnderflowException e) {
            buf.position(startPos);
            return new InvalidAnnotation(annotationType, buf, annotationSize);
        } finally {
            buf.position(nextPosition);
        }
    }

    private static SymbolAnnotation getSymbolAnnotation(ByteBuffer buf, int length) {
        Symbol[] symbols = new Symbol[length];
        for (int i = 0; i < length; i++) {
            symbols[i] = CBUtil.decodeSymbol(buf.get());
        }
        return new SymbolAnnotation(symbols);
    }

    private static CommentaryAfterMoveAnnotation getCommentaryAfterMoveAnnotation(
            ByteBuffer buf, int length) {
        if (buf.get() != 0) {
            log.warn("First byte in commentary after move annotation was not 0");
        }
        getTextLanguage(buf); // This is ignored for now
        String text = ByteBufferUtil.getFixedSizeByteString(buf, length - 2);
        return new CommentaryAfterMoveAnnotation(text);
    }

    private static CommentaryBeforeMoveAnnotation getCommentaryBeforeMoveAnnotation(
            ByteBuffer buf, int length) {
        if (buf.get() != 0) {
            log.warn("First byte in commentary after move annotation was not 0");
        }
        getTextLanguage(buf); // This is ignored for now
        String text = ByteBufferUtil.getFixedSizeByteString(buf, length - 2);
        return new CommentaryBeforeMoveAnnotation(text);
    }

    private static byte getTextLanguage(ByteBuffer buf) {
        // TODO: Implement proper language support

        // 0x00 : ALL
        // 0x2A : English
        // 0x35 : German
        // 0x31 : France
        // 0x2B : Spanish
        // 0x46 : Italian
        // 0x67 : Dutch
        // 0x75 : Portugese
        // 0x?? : Polish

        return buf.get();
    }

}
