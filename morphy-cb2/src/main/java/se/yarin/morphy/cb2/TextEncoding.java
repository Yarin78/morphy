package se.yarin.morphy.cb2;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.NotNull;

/**
 * Text whose encoding the format doesn't declare. Some texts are UTF-8 and others cp1252, within
 * one database, so text is read as UTF-8 if the bytes are valid UTF-8 and as cp1252 otherwise. It
 * is always written as UTF-8.
 */
public final class TextEncoding {
  private static final Charset CP1252 = Charset.forName("windows-1252");

  private TextEncoding() {}

  public static @NotNull String decode(byte @NotNull [] bytes) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException e) {
      return new String(bytes, CP1252);
    }
  }

  public static byte @NotNull [] encode(@NotNull String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }
}
