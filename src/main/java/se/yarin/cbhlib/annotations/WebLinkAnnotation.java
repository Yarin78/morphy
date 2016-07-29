package se.yarin.cbhlib.annotations;

import lombok.Getter;
import lombok.NonNull;
import se.yarin.cbhlib.ByteBufferUtil;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;

public class WebLinkAnnotation extends Annotation {
    @Getter @NonNull
    private final String url;

    @Getter @NonNull
    private final String text;

    public WebLinkAnnotation(@NonNull String url, @NonNull String text) {
        this.url = url;
        this.text = text;
    }

    public static WebLinkAnnotation deserialize(ByteBuffer buf) {
        int length = ByteBufferUtil.getUnsignedShortL(buf);
        // The length is just the length of the annotation (including the length field),
        // e.g. 2 + (1 + url length) + 1 + (text length)
        String url = ByteBufferUtil.getByteString(buf);
        String text = ByteBufferUtil.getByteString(buf);
        return new WebLinkAnnotation(url, text);
    }

    @Override
    public String toString() {
        return "WebLinkAnnotation = " + text + " " + url;
    }
}
