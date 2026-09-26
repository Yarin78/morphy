package se.yarin.morphy.text;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.chessbase.text.TextContentsModel;
import se.yarin.morphy.chessbase.text.TextLanguage;
import se.yarin.morphy.exceptions.MorphyMoveDecodingException;
import se.yarin.util.ByteBufferUtil;

/** Reads and writes the body of a guiding text in the v1 {@code .cbg} format. */
public final class TextContentsSerializer {
  private static final Logger log = LoggerFactory.getLogger(TextContentsSerializer.class);

  private TextContentsSerializer() {}

  public static TextContentsModel deserialize(int gameId, ByteBuffer buf)
      throws MorphyMoveDecodingException {
    try {
      int size = ByteBufferUtil.getIntB(buf);
      if ((size & (1 << 31)) == 0) {
        log.warn("Most significant bit in size in blob in text should be set");
      }
      size &= ~(1 << 31);

      int textFormat = ByteBufferUtil.getSignedShortL(buf);
      if (textFormat != 1 && textFormat != 2 && textFormat != 3) {
        log.warn("Unknown text format {} in text with id {}", textFormat, gameId);
      }

      HashMap<TextLanguage, String> titles = new HashMap<>();
      int numTitles = ByteBufferUtil.getSignedShortL(buf);
      for (int i = 0; i < numTitles; i++) {
        int languageId = ByteBufferUtil.getSignedShortL(buf);
        int titleLength = ByteBufferUtil.getUnsignedShortL(buf);
        String title = ByteBufferUtil.getFixedSizeByteString(buf, titleLength, true);
        titles.put(TextLanguage.values()[languageId], title);
      }

      int unknown = ByteBufferUtil.getSignedByte(buf);
      if ((unknown != 0 && unknown != 1) || (unknown == 0 && textFormat == 3)) {
        // 0 and 1 seen for text format 1
        // 1 seen for text format 3
        // Log if other combinations found
        log.warn(
            "Unknown value was "
                + unknown
                + " in text with id "
                + gameId
                + " with format "
                + textFormat);
      }

      HashMap<TextLanguage, String> contents = new HashMap<>();
      HashMap<TextLanguage, byte[]> formatting = new HashMap<>();

      if (textFormat == 1 || textFormat == 2) {
        int numTxt = ByteBufferUtil.getSignedShortL(buf);
        for (int i = 0; i < numTxt; i++) {
          int languageId = ByteBufferUtil.getSignedShortL(buf);
          int txtLength =
              textFormat == 1 ? ByteBufferUtil.getUnsignedShortL(buf) : ByteBufferUtil.getIntL(buf);
          String txt = ByteBufferUtil.getFixedSizeByteString(buf, txtLength, true);
          contents.put(TextLanguage.values()[languageId], txt);

          int formattingSize =
              textFormat == 1 ? ByteBufferUtil.getUnsignedShortL(buf) : ByteBufferUtil.getIntL(buf);
          byte[] format = new byte[formattingSize];
          buf.get(format, 0, formattingSize);
          formatting.put(TextLanguage.values()[languageId], format);
        }
      }
      if (textFormat == 3) {
        int numHtml = ByteBufferUtil.getSignedShortL(buf);
        for (int i = 0; i < numHtml; i++) {
          int languageId = ByteBufferUtil.getSignedShortL(buf);
          int htmlLength = ByteBufferUtil.getIntL(buf);
          String htmlText = ByteBufferUtil.getFixedSizeByteString(buf, htmlLength, true);
          contents.put(TextLanguage.values()[languageId], htmlText);
          int unknownInt = ByteBufferUtil.getIntL(buf);
          if (unknownInt != 0) {
            log.warn("Unknown trailing value {} in text with id {}", unknownInt, gameId);
          }
        }
      }

      if (buf.position() != size) {
        log.warn(
            "Size of text blob was "
                + size
                + " but was at position "
                + buf.position()
                + " after deserialization");
      }

      return new TextContentsModel(textFormat, titles, contents, formatting, unknown);
    } catch (BufferUnderflowException e) {
      log.warn("Move data ended abruptly in text {}.", gameId);
      throw new MorphyMoveDecodingException(
          "Moves data header ended abruptly in text " + gameId, e);
    }
  }

  public static String deserializeTitle(int gameId, ByteBuffer buf) {
    // Quick deserialize the first available title
    buf.position(buf.position() + 10);
    int titleLength = ByteBufferUtil.getSignedShortL(buf);
    return ByteBufferUtil.getFixedSizeByteString(buf, titleLength, true);
  }

  public static ByteBuffer serialize(@NotNull TextContentsModel model) {
    if (model.format() != 1 && model.format() != 2 && model.format() != 3) {
      throw new IllegalStateException("Can't serialize text format " + model.format());
    }

    int size = 11;

    for (TextLanguage language : TextLanguage.values()) {
      if (model.titles().containsKey(language)) {
        size += 4 + model.titles().get(language).length();
      }
      if (model.contents().containsKey(language)) {
        if (model.format() == 1) {
          size += 4 + model.contents().get(language).length();
          size += 2 + model.formatting().get(language).length;
        } else if (model.format() == 2) {
          size += 6 + model.contents().get(language).length();
          size += 4 + model.formatting().get(language).length;
        } else if (model.format() == 3) {
          size += 10 + model.contents().get(language).length();
        }
      }
    }

    ByteBuffer buf = ByteBuffer.allocate(size);
    ByteBufferUtil.putIntB(buf, size + (1 << 31));
    ByteBufferUtil.putShortL(buf, model.format());

    ByteBufferUtil.putShortL(buf, model.titles().size());
    for (TextLanguage language : TextLanguage.values()) {
      if (model.titles().containsKey(language)) {
        String title = model.titles().get(language);
        ByteBufferUtil.putShortL(buf, language.ordinal());
        ByteBufferUtil.putShortL(buf, title.length());
        ByteBufferUtil.putRawByteString(buf, title);
      }
    }

    ByteBufferUtil.putByte(buf, model.unknown());
    ByteBufferUtil.putShortL(buf, model.contents().size());

    if (model.format() == 1 || model.format() == 2) {
      for (TextLanguage language : TextLanguage.values()) {
        if (model.contents().containsKey(language)) {
          String content = model.contents().get(language);
          ByteBufferUtil.putShortL(buf, language.ordinal());
          if (model.format() == 1) {
            ByteBufferUtil.putShortL(buf, content.length());
          } else {
            ByteBufferUtil.putIntL(buf, content.length());
          }
          ByteBufferUtil.putRawByteString(buf, content);

          byte[] contentFormat = model.formatting().get(language);
          if (model.format() == 1) {
            ByteBufferUtil.putShortL(buf, contentFormat.length);
          } else {
            ByteBufferUtil.putIntL(buf, contentFormat.length);
          }
          buf.put(contentFormat);
        }
      }
    }
    if (model.format() == 3) {
      for (TextLanguage language : TextLanguage.values()) {
        if (model.contents().containsKey(language)) {
          String content = model.contents().get(language);
          ByteBufferUtil.putShortL(buf, language.ordinal());
          ByteBufferUtil.putIntL(buf, content.length());
          ByteBufferUtil.putRawByteString(buf, content);
          ByteBufferUtil.putIntL(buf, 0);
        }
      }
    }
    if (buf.position() != size) {
      log.warn(
          String.format(
              "Serialized size of text model doesn't match expected size (%d != %d)",
              size, buf.position()));
    }
    buf.flip();
    return buf;
  }
}
