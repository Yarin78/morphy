package yarin.cbhlib.annotations;

import yarin.cbhlib.ByteBufferUtil;
import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.cbhlib.Language;
import yarin.chess.GamePosition;

import java.nio.ByteBuffer;

public abstract class TextAnnotation extends Annotation {
    private String text;
    private Language textLanguage;

    public String getText() {
        return text;
    }

    public Language getTextLanguage() {
        return textLanguage;
    }

    public TextAnnotation(ByteBuffer data, int textLength) throws CBHFormatException {
        if (data.get() != 0)
            throw new CBHFormatException("Unexpected first byte for text annotation");
        textLanguage = Language.All;
        switch (data.get()) {
            case 0x00:
                textLanguage = Language.All;
                break;
            case 0x2A:
                textLanguage = Language.English;
                break;
            case 0x35:
                textLanguage = Language.German;
                break;
            case 0x31:
                textLanguage = Language.France;
                break;
            case 0x2B:
                textLanguage = Language.Spanish;
                break;
            case 0x46:
                textLanguage = Language.Italian;
                break;
            case 0x67:
                textLanguage = Language.Dutch;
                break;
            case 0x75:
                textLanguage = Language.Portugese;
                break;
            //case 0x00 : textLanguage = Language.Polish; break; // This is a bug in CB, polish has same byte code as All
        }
        text = ByteBufferUtil.getZeroTerminatedString(data, textLength);
    }
}
