package se.yarin.morphy.util;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.chess.Date;
import se.yarin.chess.Eco;
import se.yarin.chess.GameResult;
import se.yarin.morphy.entities.Nation;
import se.yarin.morphy.entities.TournamentTimeControl;
import se.yarin.morphy.entities.TournamentType;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Contains various utility functions for reading and parsing ChessBase data files. */
public final class CBUtil {
  private static final Logger log = LoggerFactory.getLogger(CBUtil.class);

  private CBUtil() {}

  /**
   * <h1>Character sets used</h1>
   * <p>ChessBase mixes 1 byte fixed-width charsets (like ISO_8859_1 aka ISO-LATIN)
   * and variable-width charsets (UTF-8) depending on where the string is stored.
   *
   * <p>In general, if there's a fixed-size slot to store a string in some record (e.g. 40 bytes),
   * UTF-8 seems to be used. The only exception is Player names that are always fixed-width.
   *
   * <p>Otherwise, 1 byte fixed-width charset are used. Which one probably depends
   * on the string. Morphy currently always uses ISO_8859_1.
   *
   * <p>A strange exception seem to be Annotators where the same name can either be UTF-8
   * or ISO_8859_1 in the same database, depending on how it got created.</p>
   */
  public static Charset cbDefaultSingleByteCharset = StandardCharsets.ISO_8859_1; // WINDOWS-1252

  public static int compareString(String s1, String s2) {
    // Ordering is done on byte level
    ByteBuffer b1 = cbDefaultSingleByteCharset.encode(s1 + "\0");
    ByteBuffer b2 = cbDefaultSingleByteCharset.encode(s2 + "\0");

    return b1.compareTo(b2);
  }

  public static int compareStringUnsigned(String s1, String s2) {
    // Same as compareString but treat each byte as unsigned instead of signed
    ByteBuffer b1 = cbDefaultSingleByteCharset.encode(s1 + "\0");
    ByteBuffer b2 = cbDefaultSingleByteCharset.encode(s2 + "\0");

    int thisPos = b1.position();
    int thisRem = b1.limit() - thisPos;
    int thatPos = b2.position();
    int thatRem = b2.limit() - thatPos;
    int length = Math.min(thisRem, thatRem);
    if (length < 0) return -1;
    int i = b1.mismatch(b2);
    if (i >= 0) {
      if (i < thisRem && i < thatRem) {
        // This is the only real difference compared to compareString above
        return Byte.compareUnsigned(b1.get(thisPos + i), b2.get(thatPos + i));
      }
      return thisRem - thatRem;
    }
    return 0;
  }

  /**
   * Decodes a 21 bit CBH encoded date to a {@link Date}.
   *
   * @param dateValue an integer containing an encoded date value
   * @return the decoded date
   */
  public static Date decodeDate(int dateValue) {
    // Bit 0-4 is day, bit 5-8 is month, bit 9-20 is year
    dateValue %= (1 << 21);
    int day = dateValue % 32;
    int month = (dateValue / 32) % 16;
    int year = dateValue / 512;
    return new Date(year, month, day);
  }

  /**
   * Converts a {@link Date} to a 21 bit CBH encoded date.
   *
   * @param date the date to encode
   * @return the encoded date
   */
  public static int encodeDate(@NotNull Date date) {
    return (date.year() * 512 + date.month() * 32 + date.day()) % (1 << 21);
  }

  /**
   * Decodes a CBH encoded Eco code to a {@link Eco}
   *
   * @param ecoValue an integer containing an encoded Eco value
   * @return the decoded Eco
   */
  public static Eco decodeEco(int ecoValue) {
    int eco = ecoValue / 128 - 1;
    int subEco = ecoValue % 128;
    return eco < 0 ? Eco.unset() : Eco.fromInt(eco, subEco);
  }

  /**
   * Converts a {@link Eco} to a CBH encoded Eco.
   *
   * @param eco the Eco to encode
   * @return the encoded Eco
   */
  public static int encodeEco(@NotNull Eco eco) {
    if (!eco.isSet()) {
      return 0;
    }
    return (eco.getInt() + 1) * 128 + eco.getSubEco();
  }

  public static GameResult decodeGameResult(int data) {
    return GameResult.values()[data];
  }

  public static int encodeGameResult(GameResult data) {
    return data.ordinal();
  }

  public static int encodeTournamentType(TournamentType type, TournamentTimeControl timeControl) {
    // bit 0-3: type
    // bit 5: blitz
    // bit 6: rapid
    // bit 7: correspondence
    // But only one of bit 5-7 is actually set
    int typeValue =
        switch (timeControl) {
          case BLITZ -> 32;
          case RAPID -> 64;
          case CORRESPONDENCE -> 128;
          default -> 0;
        };
    typeValue += type.ordinal();
    return typeValue;
  }

  public static TournamentType decodeTournamentType(int data) {
    if ((data & 31) >= TournamentType.values().length) {
      log.warn("Unknown tournament type: {}", data & 31);
      return TournamentType.NONE;
    }
    return TournamentType.values()[data & 31];
  }

  public static TournamentTimeControl decodeTournamentTimeControl(int data) {
    if ((data & 32) > 0) return TournamentTimeControl.BLITZ;
    if ((data & 64) > 0) return TournamentTimeControl.RAPID;
    if ((data & 128) > 0) return TournamentTimeControl.CORRESPONDENCE;
    return TournamentTimeControl.NORMAL;
  }

  public static Nation decodeNation(int data) {
    // TODO: Should save this value raw instead to make it more future proof
    if (data < 0 || data >= Nation.values().length) {
      return Nation.NONE;
    }
    return Nation.values()[data];
  }

  public static int encodeNation(Nation nation) {
    return nation.ordinal();
  }

  // Debug code

  public static String toHexString(ByteBuffer buf) {
    int oldPos = buf.position();
    byte[] bytes = new byte[buf.limit() - oldPos];
    buf.get(bytes);
    buf.position(oldPos);
    return toHexString(bytes);
  }

  public static String toHexString(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < bytes.length; i++) {
      int v = bytes[i];
      if (v < 0) v += 256;
      sb.append(String.format("%02X ", v));
    }
    return sb.toString();
  }

  public static byte[] fromHexString(String s) {
    s = s.replace(" ", "");
    if (s.length() % 2 != 0) {
      throw new IllegalArgumentException("Invalid length of hex string");
    }
    byte[] bytes = new byte[s.length() / 2];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
    }
    return bytes;
  }

  public static File fileWithExtension(@NotNull File file, @NotNull String extension) {
    if (!extension.startsWith(".")) {
      throw new IllegalArgumentException("Extension should start with a .");
    }
    int extensionStart = file.getPath().lastIndexOf(".");
    if (extensionStart < 0) {
      throw new IllegalArgumentException("The file must have an extension");
    }
    String base = file.getPath().substring(0, extensionStart);
    return new File(base + extension);
  }

  public static String baseName(File file) {
    // Gets the name of the file without path or extension
    String name = file.getName();
    int extensionStart = name.lastIndexOf(".");
    return extensionStart < 0 ? name : name.substring(0, extensionStart);
  }

  /**
   * Gets a normalized (lower case) version of the extension of a file
   *
   * @param file the file to get the extension
   * @return the file extension, including the period
   */
  public static String extension(File file) {
    int extensionStart = file.getPath().lastIndexOf(".");
    if (extensionStart < 0) {
      throw new IllegalArgumentException("The file must have an extension");
    }
    return file.getPath().substring(extensionStart).toLowerCase(Locale.ROOT);
  }

  /**
   * Gets the extension of a file (case preserved)
   *
   * @param file the file to get the extension
   * @return the file extension including the period, or empty string if no extension
   */
  public static String getExtension(@NotNull File file) {
    int extensionStart = file.getPath().lastIndexOf(".");
    return extensionStart < 0 ? "" : file.getPath().substring(extensionStart);
  }
}
