package se.yarin.cbhlib.annotations;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import se.yarin.cbhlib.entities.Nation;
import se.yarin.cbhlib.games.GameHeaderFlags;
import se.yarin.util.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.annotations.CommentaryAfterMoveAnnotation;

import java.nio.ByteBuffer;

@EqualsAndHashCode(callSuper = false)
public class TextAfterMoveAnnotation extends CommentaryAfterMoveAnnotation
        implements StatisticalAnnotation {

    @Getter
    private Nation language;

    @Getter
    private int unknown; // This value is sometimes 64 in Megabase 2016

    public String getText() {
        return super.getCommentary();
    }

    public TextAfterMoveAnnotation(@NonNull String commentary) {
        this(0, commentary, Nation.NONE);
    }

    public TextAfterMoveAnnotation(int unknown, @NonNull String commentary, @NonNull Nation language) {
        super(commentary);
        this.unknown = unknown;
        this.language = language;
    }

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.commentariesLength += getText().length();
        stats.flags.add(GameHeaderFlags.COMMENTARY);
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            TextAfterMoveAnnotation textAnno = (TextAfterMoveAnnotation) annotation;
            ByteBufferUtil.putByte(buf, textAnno.getUnknown());
            ByteBufferUtil.putByte(buf, textAnno.getLanguage().ordinal());
            ByteBufferUtil.putFixedSizeByteString(buf, textAnno.getText(), textAnno.getText().length());
        }

        @Override
        public TextAfterMoveAnnotation deserialize(ByteBuffer buf, int length) {
            int unknown = buf.get();
            Nation language = Nation.values()[ByteBufferUtil.getUnsignedByte(buf)];
            String comment = ByteBufferUtil.getFixedSizeByteString(buf, length - 2);
            return new TextAfterMoveAnnotation(unknown, comment, language);
        }

        @Override
        public Class getAnnotationClass() {
            return TextAfterMoveAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x02;
        }
    }

}
