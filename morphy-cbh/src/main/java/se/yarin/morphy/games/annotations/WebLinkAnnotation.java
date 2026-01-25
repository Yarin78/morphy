package se.yarin.morphy.games.annotations;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.util.ByteBufferUtil;
import se.yarin.morphy.games.GameHeaderFlags;
import se.yarin.chess.annotations.Annotation;

import java.nio.ByteBuffer;
import java.util.regex.Pattern;

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

  public static class PgnCodec implements AnnotationPgnCodec {
    private static final Logger log = LoggerFactory.getLogger(PgnCodec.class);
    private static final Pattern WEBLINK_PATTERN = Pattern.compile("\\[%weblink\\s+([^\\]]+)\\]");

    @Override
    @NotNull
    public Pattern getPattern() {
      return WEBLINK_PATTERN;
    }

    @Override
    @Nullable
    public String encode(@NotNull Annotation annotation) {
      WebLinkAnnotation a = (WebLinkAnnotation) annotation;
      return "[%weblink \"" + AnnotationPgnUtil.escapeString(a.url()) + "\" \"" + AnnotationPgnUtil.escapeString(a.text()) + "\"]";
    }

    @Override
    @Nullable
    public Annotation decode(@NotNull String data) {
      try {
        // The data contains two quoted strings: "url" "text"
        // We need to parse them separately
        int firstQuote = data.indexOf('"');
        if (firstQuote == -1) {
          log.warn("Invalid weblink format - no opening quote found: {}", data);
          return null;
        }

        // Find the closing quote for the first string
        int secondQuote = firstQuote + 1;
        boolean escaped = false;
        while (secondQuote < data.length()) {
          char c = data.charAt(secondQuote);
          if (escaped) {
            escaped = false;
          } else if (c == '\\') {
            escaped = true;
          } else if (c == '"') {
            break;
          }
          secondQuote++;
        }

        if (secondQuote >= data.length()) {
          log.warn("Invalid weblink format - unclosed first quoted string: {}", data);
          return null;
        }

        String urlQuoted = data.substring(firstQuote, secondQuote + 1);
        String url = AnnotationPgnUtil.parseQuotedString(urlQuoted);

        // Find the second quoted string
        int thirdQuote = data.indexOf('"', secondQuote + 1);
        if (thirdQuote == -1) {
          log.warn("Invalid weblink format - no second opening quote found: {}", data);
          return null;
        }

        // Find the closing quote for the second string
        int fourthQuote = thirdQuote + 1;
        escaped = false;
        while (fourthQuote < data.length()) {
          char c = data.charAt(fourthQuote);
          if (escaped) {
            escaped = false;
          } else if (c == '\\') {
            escaped = true;
          } else if (c == '"') {
            break;
          }
          fourthQuote++;
        }

        if (fourthQuote >= data.length()) {
          log.warn("Invalid weblink format - unclosed second quoted string: {}", data);
          return null;
        }

        String textQuoted = data.substring(thirdQuote, fourthQuote + 1);
        String text = AnnotationPgnUtil.parseQuotedString(textQuoted);

        return ImmutableWebLinkAnnotation.of(url, text);
      } catch (Exception e) {
        log.warn("Failed to parse weblink: {}", data, e);
        return null;
      }
    }

    @Override
    @NotNull
    public Class<? extends Annotation> getAnnotationClass() {
      return ImmutableWebLinkAnnotation.class;
    }
  }
}
