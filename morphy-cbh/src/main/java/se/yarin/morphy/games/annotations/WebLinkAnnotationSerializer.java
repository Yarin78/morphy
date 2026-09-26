package se.yarin.morphy.games.annotations;

import java.nio.ByteBuffer;
import se.yarin.chess.annotations.Annotation;
import se.yarin.morphy.chessbase.annotations.*;
import se.yarin.util.ByteBufferUtil;

/** Reads and writes a {@link WebLinkAnnotation} in the v1 {@code .cba} format. */
public class WebLinkAnnotationSerializer implements AnnotationSerializer {
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
