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

    public static Annotation getFromData(ByteBuffer annotationData) {
        int annotationType = ByteBufferUtil.getUnsignedByte(annotationData);
        short noBytes = annotationData.getShort();
        int nextPosition = annotationData.position() + noBytes - 6;

        try {
            switch (annotationType)
            {
                case 0x02 : return new TextAfterMoveAnnotation(annotationData, noBytes - 8);
                case 0x03 : return new SymbolAnnotation(annotationData, noBytes - 6);
                case 0x04 : return new GraphicalSquaresAnnotation(annotationData, noBytes - 6);
                case 0x05 : return new GraphicalArrowsAnnotation(annotationData, noBytes - 6);
                case 0x18 : return new CriticalPositionAnnotation(annotationData);
                case 0x82 : return new TextBeforeMoveAnnotation(annotationData, noBytes - 8);
//                case 0x14 : return new PawnStructureAnnotation(annotationData);
//                case 0x15 : return new PiecePathAnnotation(annotationData);
//                case 0x13 : return new GameQuotationAnnotation(annotationData);
//                case 0x22 : return new MedalAnnotation(annotationData);
//                case 0x23 : return new VariationColorAnnotation(annotationData);
//                case 0x10 : return new SoundAnnotation(annotationData);
//                case 0x20 : return new VideoAnnotation(annotationData);
//                case 0x11 : return new PictureAnnotation(annotationData);
//                case 0x09 : return new TrainingAnnotation(annotationData);
//                case 0x61 : return new CorrespondenceHeaderAnnotation(annotationData);
//                case 0x19 : return new CorrespondenceMoveAnnotation(annotationData);
                default :
//                    log.warn(String.format("Unknown annotation type %d containing %d bytes of data",                            annotationType, noBytes - 6));
                    return new UnknownAnnotation(annotationType, annotationData, noBytes-6);
            }
        } catch (CBHFormatException e) {
            return new InvalidAnnotation(annotationType, annotationData, noBytes-6);
        } finally {
            annotationData.position(nextPosition);
        }
    }

    protected Annotation()
    {
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










