package yarin.cbhlib.annotations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yarin.cbhlib.AnnotatedGame;
import yarin.cbhlib.ByteBufferUtil;
import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;
import java.util.List;

public abstract class Annotation
{
    private static final Logger log = LoggerFactory.getLogger(Annotation.class);

    public enum GraphicalColor {
        NONE(0),
        NOT_USED(1),
        GREEN(2),
        YELLOW(3),
        RED(4);

        private final int colorId;

        GraphicalColor(int colorId) {
            this.colorId = colorId;
        }
    }

    private GamePosition position;

    /**
     * Gets the position in the game where this annotation applies.
     * For annotations that refer to moves, those refer to the last move
     * made in this position.
     * Returns null if the annotation applies to the whole game.
     */
    public GamePosition getPosition()
    {
        return position;
    }

    public static Annotation getFromData(GamePosition startPosition, List<GamePosition> positionsInOrder, ByteBuffer annotationData)
            throws CBHFormatException {
        // TODO: Should never throw exception; if parser fails anywhere, return InvalidAnnotation with all data
        int moveNo = ByteBufferUtil.getSignedBigEndian24BitValue(annotationData);
        int annotationType = ByteBufferUtil.getUnsignedByte(annotationData);
        short noBytes = annotationData.getShort();
        int nextAnnotationPosition = annotationData.position() + noBytes - 6;

        log.debug("Found annotation of type " + annotationType + " at move " + moveNo);

        try {
            GamePosition position;
            if (moveNo == -1) {
                position = startPosition;
            } else if (moveNo >= 0 && moveNo < positionsInOrder.size()) {
                position = positionsInOrder.get(moveNo);
            } else {
                log.warn("Annotation referred to illegal game position; skipping annotation");
                return new UnknownAnnotation(startPosition, annotationType, annotationData, noBytes);
            }

            switch (annotationType)
            {
                case 0x02 : return new TextAfterMoveAnnotation(position, annotationData, noBytes - 8);
                case 0x03 : return new SymbolAnnotation(position, annotationData, noBytes - 6);
                case 0x04 : return new GraphicalSquaresAnnotation(position, annotationData, noBytes - 6);
                case 0x05 : return new GraphicalArrowsAnnotation(position, annotationData, noBytes - 6);
//                case 0x18 : return new CriticalPositionAnnotation(position, annotationData);
                case 0x82 : return new TextBeforeMoveAnnotation(position, annotationData, noBytes - 8);
//                case 0x14 : return new PawnStructureAnnotation(position, annotationData);
//                case 0x15 : return new PiecePathAnnotation(position, annotationData);
//                case 0x13 : return new GameQuotationAnnotation(position, annotationData);
//                case 0x22 : return new MedalAnnotation(position, annotationData);
//                case 0x23 : return new VariationColorAnnotation(position, annotationData);
//                case 0x10 : return new SoundAnnotation(position, annotationData);
//                case 0x20 : return new VideoAnnotation(position, annotationData);
//                case 0x11 : return new PictureAnnotation(position, annotationData);
//                case 0x09 : return new TrainingAnnotation(position, annotationData);
//                case 0x61 : return new CorrespondenceHeaderAnnotation(position, annotationData);
//                case 0x19 : return new CorrespondenceMoveAnnotation(position, annotationData);
                default :
                    log.warn(String.format("Found unknown annotation type %d at move %d containing %d bytes of data",
                            annotationType, moveNo, noBytes - 6));
                    return new UnknownAnnotation(position, annotationType, annotationData, noBytes-6);
            }
        } finally {
            annotationData.position(nextAnnotationPosition);
        }
    }

    protected Annotation(GamePosition annotationPosition)
    {
        position = annotationPosition;
    }

    public String getPreText()
    {
        return null;
    }

    public String getPostText()
    {
        return null;
    }
}










