package yarin.cbhlib.annotations;

import yarin.cbhlib.Language;
import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public class TextAfterMoveAnnotation extends TextAnnotation {
    public TextAfterMoveAnnotation(ByteBuffer data, int textLength)
            throws CBHFormatException {
        super(data, textLength);
    }

    public String getPostText() {
        if (getTextLanguage() == Language.All || getTextLanguage() == Language.English)
            return " " + getText();
        return null;
    }
}
