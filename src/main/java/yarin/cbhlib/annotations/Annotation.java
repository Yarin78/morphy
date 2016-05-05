package yarin.cbhlib.annotations;

import yarin.cbhlib.ByteBufferUtil;
import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;
import java.util.List;

public abstract class Annotation
{
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

    public static Annotation getFromData(List<GamePosition> positionsInOrder, ByteBuffer annotationData, boolean throwOnUnknown) throws CBHFormatException {
        int moveNo = ByteBufferUtil.getSignedBigEndian24BitValue(annotationData);

        GamePosition position;
        if (moveNo == -1)
            position = null;
        else if (moveNo >= 0 && moveNo < positionsInOrder.size())
            position = positionsInOrder.get(moveNo);
        else
            throw new CBHFormatException("Illegal move number for annotation");

        int annotationType = ByteBufferUtil.getUnsignedByte(annotationData);
        short noBytes = annotationData.getShort();
        int nextAnnotationPosition = annotationData.position() + noBytes;
        try {
            switch (annotationType)
            {
                case 0x02 : return new TextAfterMoveAnnotation(position, annotationData, noBytes - 8);
                case 0x03 : return new SymbolAnnotation(position, annotationData, noBytes - 6);
                case 0x18 : return new CriticalPositionAnnotation(position, annotationData);
                case 0x82 : return new TextBeforeMoveAnnotation(position, annotationData, noBytes - 8);
                case 0x14 : return new PawnStructureAnnotation(position, annotationData);
                case 0x15 : return new PiecePathAnnotation(position, annotationData);
                case 0x13 : return new GameQuotationAnnotation(position, annotationData);
                case 0x22 : return new MedalAnnotation(position, annotationData);
                case 0x23 : return new VariationColorAnnotation(position, annotationData);
                case 0x10 : return new SoundAnnotation(position, annotationData);
                case 0x20 : return new VideoAnnotation(position, annotationData);
                case 0x11 : return new PictureAnnotation(position, annotationData);
                case 0x09 : return new TrainingAnnotation(position, annotationData);
                case 0x61 : return new CorrespondenceHeaderAnnotation(position, annotationData);
                case 0x19 : return new CorrespondenceMoveAnnotation(position, annotationData);
                case 0x05 :
                    return null; // TODO: Found in Ari Ziegler French defence
                default :
                    if (throwOnUnknown)
                        throw new CBHFormatException(String.format("Unknown annotation type: 0x{%02X}", annotationType));
                    return null;
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










