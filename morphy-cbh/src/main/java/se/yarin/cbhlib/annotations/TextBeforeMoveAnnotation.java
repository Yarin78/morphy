package se.yarin.cbhlib.annotations;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import se.yarin.util.ByteBufferUtil;
import se.yarin.cbhlib.games.GameHeaderFlags;
import se.yarin.cbhlib.entities.Nation;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.annotations.CommentaryBeforeMoveAnnotation;

import java.nio.ByteBuffer;

@EqualsAndHashCode(callSuper = false)
public class TextBeforeMoveAnnotation extends CommentaryBeforeMoveAnnotation
        implements StatisticalAnnotation {

    @Getter
    private Nation language;

    @Getter
    private int unknown;

    public String getText() {
        return super.getCommentary();
    }

    public TextBeforeMoveAnnotation(@NonNull String commentary) {
        this(0, commentary, Nation.NONE);
    }

    public TextBeforeMoveAnnotation(int unknown, @NonNull String commentary, @NonNull Nation language) {
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
            TextBeforeMoveAnnotation textAnno = (TextBeforeMoveAnnotation) annotation;
            ByteBufferUtil.putByte(buf, textAnno.getUnknown());
            ByteBufferUtil.putByte(buf, textAnno.getLanguage().ordinal());
            ByteBufferUtil.putFixedSizeByteString(buf, textAnno.getText(), textAnno.getText().length());
        }

        @Override
        public TextBeforeMoveAnnotation deserialize(ByteBuffer buf, int length) {
            int unknown = buf.get();
            Nation language = Nation.values()[ByteBufferUtil.getUnsignedByte(buf)];
            String comment = ByteBufferUtil.getFixedSizeByteString(buf, length - 2);
            return new TextBeforeMoveAnnotation(unknown, comment, language);
        }

        @Override
        public Class getAnnotationClass() {
            return TextBeforeMoveAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x82;
        }
    }
}
