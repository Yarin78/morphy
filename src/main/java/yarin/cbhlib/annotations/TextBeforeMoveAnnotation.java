package yarin.cbhlib.annotations;

import yarin.cbhlib.Language;
import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class TextBeforeMoveAnnotation extends TextAnnotation {
    public TextBeforeMoveAnnotation(GamePosition annotationPosition, ByteBuffer data, int textLength) throws CBHFormatException {
        super(annotationPosition, data, textLength);
    }

    public String getPreText() {
        if (getTextLanguage() == Language.All || getTextLanguage() == Language.English)
            return getText() + " ";
        return null;
    }
}
