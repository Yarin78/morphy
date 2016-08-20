package se.yarin.cbhlib.annotations;

import lombok.Getter;
import lombok.NonNull;
import se.yarin.cbhlib.ByteBufferUtil;
import se.yarin.cbhlib.Nation;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.annotations.CommentaryAfterMoveAnnotation;

import java.nio.ByteBuffer;
import java.util.*;

public class TextAfterMoveAnnotation extends CommentaryAfterMoveAnnotation {

    private Map<Nation, String> text = new HashMap<>();

    @Getter
    private int unknown; // This value is sometimes 64 in Megabase 2016

    /**
     * Gets the text in all languages
     */
    public String getText() {
        return super.getCommentary();
    }

    /**
     * Gets the text in the specified language if it exists, otherwise in the unspecified language.
     */
    public String getText(@NonNull Nation language) {
        if (text.containsKey(language)) {
            return text.get(language);
        }
        if (text.containsKey(Nation.NONE)) {
            return text.get(Nation.NONE);
        }
        return "";
    }

    /**
     * Gets all the languages this comment is available in
     */
    public Set<Nation> getLanguages() {
        return text.keySet();
    }

    public TextAfterMoveAnnotation(@NonNull String commentary) {
        this(0, commentary, Nation.NONE);
    }

    public TextAfterMoveAnnotation(int unknown, @NonNull String commentary, @NonNull Nation language) {
        super(commentary);
        this.unknown = unknown;
        text.put(language, commentary);
    }

    private static String combineTexts(Collection<String> text) {
        StringJoiner joiner = new StringJoiner(" ");
        text.forEach(joiner::add);
        return joiner.toString();
    }

    private TextAfterMoveAnnotation(@NonNull Map<Nation, String> text) {
        super(combineTexts(text.values()));
        this.text = text;
    }

    public static TextAfterMoveAnnotation deserialize(ByteBuffer buf, int length) {
        int unknown = buf.get();
        Nation language = Nation.values()[ByteBufferUtil.getUnsignedByte(buf)];
        String comment = ByteBufferUtil.getFixedSizeByteString(buf, length - 2);
        return new TextAfterMoveAnnotation(unknown, comment, language);
    }

}
