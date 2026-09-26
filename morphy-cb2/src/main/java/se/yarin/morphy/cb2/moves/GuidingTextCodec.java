package se.yarin.morphy.cb2.moves;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.cb2.InvalidDataException;
import se.yarin.morphy.cb2.TextEncoding;
import se.yarin.morphy.chessbase.text.TextContentsModel;
import se.yarin.morphy.chessbase.text.TextLanguage;

/**
 * Converts between the content of a guiding text's {@code .2cbg} record and a {@link
 * TextContentsModel}: a format version, and the HTML of the text in each language, the language
 * given as a nation code. The title of a text is not stored here but in a game tag entity.
 */
public final class GuidingTextCodec {

  private static final int VERSION = 5;

  private static final Map<TextLanguage, Integer> CODES =
      Map.of(
          TextLanguage.ENGLISH, 42,
          TextLanguage.SPANISH, 43,
          TextLanguage.FRENCH, 49,
          TextLanguage.GERMAN, 53,
          TextLanguage.ITALIAN, 70,
          TextLanguage.DUTCH, 103,
          TextLanguage.PORTUGUESE, 117,
          TextLanguage.UNKNOWN, 0);

  /** The languages ChessBase writes an entry for in every text, in the order stored. */
  private static final TextLanguage[] OFFERED = {
    TextLanguage.ENGLISH,
    TextLanguage.SPANISH,
    TextLanguage.FRENCH,
    TextLanguage.GERMAN,
    TextLanguage.ITALIAN,
    TextLanguage.DUTCH,
    TextLanguage.PORTUGUESE
  };

  private GuidingTextCodec() {}

  /** The language a nation code stands for, or null if it's not one used for texts. */
  public static @Nullable TextLanguage language(int code) {
    for (Map.Entry<TextLanguage, Integer> e : CODES.entrySet()) {
      if (e.getValue() == code) {
        return e.getKey();
      }
    }
    return null;
  }

  /** The nation code of a language. */
  public static int code(@NotNull TextLanguage language) {
    return CODES.get(language);
  }

  /**
   * Decodes the body of a text. Entries with no text are kept, as empty strings, so the text is
   * written back with the same entries.
   *
   * @throws InvalidDataException if the content is not a text body
   */
  public static @NotNull TextContentsModel decode(byte @NotNull [] content) {
    ByteBuffer buf = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
    try {
      int version = buf.getShort();
      if (version != VERSION) {
        throw new InvalidDataException("Unknown text format " + version);
      }
      int remaining = buf.getInt();
      if (remaining != buf.remaining()) {
        throw new InvalidDataException("The text has the wrong length");
      }
      int count = buf.getInt();
      Map<TextLanguage, String> contents = new EnumMap<>(TextLanguage.class);
      for (int i = 0; i < count; i++) {
        int code = buf.getInt();
        byte[] html = new byte[buf.getInt()];
        buf.get(html);
        TextLanguage language = language(code);
        if (language == null) {
          throw new InvalidDataException("Unknown text language " + code);
        }
        contents.put(language, TextEncoding.decode(html));
      }
      return new TextContentsModel(3, new HashMap<>(), contents, new HashMap<>(), 0);
    } catch (BufferUnderflowException | NegativeArraySizeException e) {
      throw new InvalidDataException("The text ended abruptly", e);
    }
  }

  /**
   * Encodes the body of a text: an entry for each language ChessBase offers, empty if the text
   * has none in it, and for any other language the text has, in ascending order of code.
   */
  public static byte @NotNull [] encode(@NotNull TextContentsModel model) {
    TreeMap<Integer, byte[]> entries = new TreeMap<>();
    for (TextLanguage language : OFFERED) {
      entries.put(code(language), new byte[0]);
    }
    for (Map.Entry<TextLanguage, String> e : model.contents().entrySet()) {
      entries.put(code(e.getKey()), TextEncoding.encode(e.getValue()));
    }
    int size = 10;
    for (byte[] html : entries.values()) {
      size += 8 + html.length;
    }
    ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    buf.putShort((short) VERSION);
    buf.putInt(size - 6);
    buf.putInt(entries.size());
    for (Map.Entry<Integer, byte[]> e : entries.entrySet()) {
      buf.putInt(e.getKey());
      buf.putInt(e.getValue().length);
      buf.put(e.getValue());
    }
    return buf.array();
  }
}
