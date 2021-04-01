package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import se.yarin.util.ByteBufferUtil;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

@Value.Immutable
public abstract class WebLinkAnnotation extends Annotation implements StatisticalAnnotation {
    @Value.Parameter
    @NotNull
    public abstract String url();

    @Value.Parameter
    @NotNull
    public abstract String text();

    @Override
    public String toString() {
        return "WebLinkAnnotation = " + text() + " " + url();
    }

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.flags.add(GameHeaderFlags.WEB_LINK);
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            WebLinkAnnotation wla = (WebLinkAnnotation) annotation;
            ByteBufferUtil.putShortL(buf, wla.url().length() + wla.text().length() + 4);
            ByteBufferUtil.putByteString(buf, wla.url());
            ByteBufferUtil.putByteString(buf, wla.text());
        }

        @Override
        public WebLinkAnnotation deserialize(ByteBuffer buf, int length) {
            length = ByteBufferUtil.getUnsignedShortL(buf);
            // The length is just the length of the annotation (including the length field),
            // e.g. 2 + (1 + url length) + 1 + (text length)
            String url = ByteBufferUtil.getByteString(buf);
            String text = ByteBufferUtil.getByteString(buf);
            return ImmutableWebLinkAnnotation.of(url, text);
        }

        @Override
        public Class getAnnotationClass() {
            return ImmutableWebLinkAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x1C;
        }
    }
}
