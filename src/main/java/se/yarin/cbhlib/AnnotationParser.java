package se.yarin.cbhlib;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.annotations.*;
import se.yarin.cbhlib.annotations.CriticalPositionAnnotation;
import se.yarin.cbhlib.annotations.GraphicalArrowsAnnotation;
import se.yarin.cbhlib.annotations.GraphicalSquaresAnnotation;
import se.yarin.cbhlib.annotations.InvalidAnnotation;
import se.yarin.cbhlib.annotations.UnknownAnnotation;
import se.yarin.chess.Symbol;
import se.yarin.chess.annotations.*;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class AnnotationParser {

    private static final Logger log = LoggerFactory.getLogger(AnnotationParser.class);

    /**
     * Gets a ChessBase annotation from a {@link ByteBuffer}. Some ChessBae annotation
     * might be split up into multiple {@link Annotation}, hence the return of a list.
     * If an annotation is of an unknown type, {@link UnknownAnnotation} will be returned.
     * If there was some error parsing the annotation data, {@link InvalidAnnotation} will be returned.
     * @param buf the byte buffer
     * @return an annotation
     */
    public static Annotation getAnnotation(@NonNull ByteBuffer buf) {
        // All annotation data is stored in Big Endian
        // TODO: Really? Maybe it's just the noBytes below that should be treated as one byte
        int startPos = buf.position();
        int annotationType = ByteBufferUtil.getUnsignedByte(buf);
        short noBytes = ByteBufferUtil.getSignedShortB(buf);
        int nextPosition = buf.position() + noBytes - 6;

        try {
            switch (annotationType)
            {
                // TODO: This noBytes - 8 is odd, why isn't it noBytes-6? Check if this really is the case.
                case 0x02 : return getCommentaryAfterMoveAnnotation(buf, noBytes-8);
                case 0x03 : return getSymbolAnnotation(buf, noBytes - 6);
                case 0x04 : return getGraphicalSquaresAnnotation(buf, noBytes - 6);
                case 0x05 : return getGraphicalArrowsAnnotation(buf, noBytes - 6);
                case 0x18 : return getCriticalPositionAnnotation(buf);
                case 0x82 : return getCommentaryBeforeMoveAnnotation(buf, noBytes - 8);
                case 0x25 : return new VideoStreamTimeAnnotation(ByteBufferUtil.getIntB(buf));

//                case 0x14 : return new PawnStructureAnnotation(buf);
//                case 0x15 : return new PiecePathAnnotation(buf);
//                case 0x13 : return new GameQuotationAnnotation(buf);
//                case 0x22 : return new MedalAnnotation(buf);
//                case 0x23 : return new VariationColorAnnotation(buf);
//                case 0x10 : return new SoundAnnotation(buf);
//                case 0x20 : return new VideoAnnotation(buf);
//                case 0x11 : return new PictureAnnotation(buf);
//                case 0x09 : return new TrainingAnnotation(buf);
//                case 0x61 : return new CorrespondenceHeaderAnnotation(buf);
//                case 0x19 : return new CorrespondenceMoveAnnotation(buf);
                default :
//                    log.warn(String.format("Unknown annotation type %d containing %d bytes of data",                            annotationType, noBytes - 6));
                    return new UnknownAnnotation(annotationType, buf, noBytes-6);
            }
        } catch (ChessBaseAnnotationException | IllegalArgumentException e) {
            buf.position(startPos);
            return new InvalidAnnotation(annotationType, buf, noBytes-6);
        } finally {
            buf.position(nextPosition);
        }
    }

    private static CriticalPositionAnnotation getCriticalPositionAnnotation(ByteBuffer buf) {
        CriticalPositionAnnotation.CriticalPositionType type = CriticalPositionAnnotation.CriticalPositionType.values()[buf.get()];
        return new CriticalPositionAnnotation(type);
    }

    private static GraphicalArrowsAnnotation getGraphicalArrowsAnnotation(ByteBuffer buf, int length) throws ChessBaseAnnotationException {
        ArrayList<GraphicalArrowsAnnotation.Arrow> arrows = new ArrayList<>();
        for (int i = 0; i < length / 3; i++) {
            int color = ByteBufferUtil.getUnsignedByte(buf);
            int fromSqi = ByteBufferUtil.getUnsignedByte(buf) - 1;
            int toSqi = ByteBufferUtil.getUnsignedByte(buf) - 1;
            if (fromSqi < 0 || fromSqi > 63 || toSqi < 0 || toSqi > 63
                    || color < 0 || color > GraphicalAnnotationColor.maxColor())
                throw new ChessBaseAnnotationException("Invalid graphical arrows annotation");
            arrows.add(new GraphicalArrowsAnnotation.Arrow(GraphicalAnnotationColor.fromInt(color), fromSqi, toSqi));
        }
        return new GraphicalArrowsAnnotation(arrows);
    }

    private static GraphicalSquaresAnnotation getGraphicalSquaresAnnotation(ByteBuffer buf, int length) throws ChessBaseAnnotationException {
        ArrayList<GraphicalSquaresAnnotation.Square> squares = new ArrayList<>();
        for (int i = 0; i < length / 2; i++) {
            int color = ByteBufferUtil.getUnsignedByte(buf);
            int sqi = ByteBufferUtil.getUnsignedByte(buf) - 1;
            if (sqi < 0 || sqi > 63 || color < 0 || color > GraphicalAnnotationColor.maxColor())
                throw new ChessBaseAnnotationException("Invalid graphical squares annotation");
            squares.add(new GraphicalSquaresAnnotation.Square(GraphicalAnnotationColor.fromInt(color), sqi));
        }
        return new GraphicalSquaresAnnotation(squares);
    }

    private static SymbolAnnotation getSymbolAnnotation(ByteBuffer buf, int length) {
        Symbol[] symbols = new Symbol[length];
        for (int i = 0; i < length; i++) {
            symbols[i] = CBUtil.decodeSymbol(buf.get());
        }
        return new SymbolAnnotation(symbols);
    }

    private static CommentaryAfterMoveAnnotation getCommentaryAfterMoveAnnotation(
            ByteBuffer buf, int commentLength) {
        getTextLanguage(buf); // This is ignored for now
        String text = ByteBufferUtil.getByteStringZeroTerminated(buf, commentLength);
        return new CommentaryAfterMoveAnnotation(text);
    }

    private static CommentaryBeforeMoveAnnotation getCommentaryBeforeMoveAnnotation(
            ByteBuffer buf, int commentLength) {
        getTextLanguage(buf); // This is ignored for now
        String text = ByteBufferUtil.getByteStringZeroTerminated(buf, commentLength);
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
