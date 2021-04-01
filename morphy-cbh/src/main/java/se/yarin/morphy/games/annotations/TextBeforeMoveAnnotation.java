package se.yarin.morphy.games.annotations;

import lombok.NonNull;
import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import se.yarin.util.ByteBufferUtil;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.morphy.entities.Nation;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

@Value.Immutable
public abstract class TextBeforeMoveAnnotation extends Annotation
        implements StatisticalAnnotation {

    @Value.Parameter
    @NotNull
    public abstract String text();

    @Value.Default
    @NotNull
    public Nation language() { return Nation.NONE; };

    @Value.Default
    public int unknown() { return 0; }

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.commentariesLength += text().length();
        stats.flags.add(GameHeaderFlags.COMMENTARY);
    }

    @Override
    public String format(@NonNull String text, boolean ascii) {
        String s = this.text();
        if (ascii) {
            s = s.replaceAll("[^\\x20-\\x7E]", "?");
        }
        return "{ " + s + " } " + text;
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            TextBeforeMoveAnnotation textAnno = (TextBeforeMoveAnnotation) annotation;
            ByteBufferUtil.putByte(buf, textAnno.unknown());
            ByteBufferUtil.putByte(buf, textAnno.language().ordinal());
            ByteBufferUtil.putFixedSizeByteString(buf, textAnno.text(), textAnno.text().length());
        }

        @Override
        public TextBeforeMoveAnnotation deserialize(ByteBuffer buf, int length) {
            return ImmutableTextBeforeMoveAnnotation.builder()
                    .unknown(buf.get())
                    .language(Nation.values()[ByteBufferUtil.getUnsignedByte(buf)])
                    .text(ByteBufferUtil.getFixedSizeByteString(buf, length - 2))
                    .build();
        }

        @Override
        public Class getAnnotationClass() {
            return ImmutableTextBeforeMoveAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x82;
        }
    }
}
