package se.yarin.cbhlib.annotations;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import se.yarin.cbhlib.util.ByteBufferUtil;
import se.yarin.cbhlib.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

@EqualsAndHashCode(callSuper = false)
public class WebLinkAnnotation extends Annotation implements StatisticalAnnotation {
    @Getter @NonNull
    private final String url;

    @Getter @NonNull
    private final String text;

    public WebLinkAnnotation(@NonNull String url, @NonNull String text) {
        this.url = url;
        this.text = text;
    }

    @Override
    public String toString() {
        return "WebLinkAnnotation = " + text + " " + url;
    }

    @Override
    public void updateStatistics(AnnotationStatistics stats) {
        stats.flags.add(GameHeaderFlags.WEB_LINK);
    }

    public static class Serializer implements AnnotationSerializer {
        @Override
        public void serialize(ByteBuffer buf, Annotation annotation) {
            WebLinkAnnotation wla = (WebLinkAnnotation) annotation;
            ByteBufferUtil.putShortL(buf, wla.getUrl().length() + wla.getText().length() + 4);
            ByteBufferUtil.putByteString(buf, wla.getUrl());
            ByteBufferUtil.putByteString(buf, wla.getText());
        }

        @Override
        public WebLinkAnnotation deserialize(ByteBuffer buf, int length) {
            length = ByteBufferUtil.getUnsignedShortL(buf);
            // The length is just the length of the annotation (including the length field),
            // e.g. 2 + (1 + url length) + 1 + (text length)
            String url = ByteBufferUtil.getByteString(buf);
            String text = ByteBufferUtil.getByteString(buf);
            return new WebLinkAnnotation(url, text);
        }

        @Override
        public Class getAnnotationClass() {
            return WebLinkAnnotation.class;
        }

        @Override
        public int getAnnotationType() {
            return 0x1C;
        }
    }
}
