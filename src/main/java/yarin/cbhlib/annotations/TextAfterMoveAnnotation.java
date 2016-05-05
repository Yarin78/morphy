package yarin.cbhlib.annotations;

import yarin.cbhlib.Language;
import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class TextAfterMoveAnnotation extends TextAnnotation {
    public TextAfterMoveAnnotation(GamePosition annotationPosition, ByteBuffer data, int textLength)
            throws CBHFormatException {
        super(annotationPosition, data, textLength);
    }

    public String getPostText() {
        if (getTextLanguage() == Language.All)
            return " " + getText();
        return null;
    }
}
