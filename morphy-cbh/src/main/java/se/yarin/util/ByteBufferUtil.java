package se.yarin.util;

import org.jetbrains.annotations.NotNull;
import org.mozilla.universalchardet.UniversalDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.util.CBUtil;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Class containing static methods for getting data from a ByteBuffer by explicitly telling if it is
 * stored as little endian or big endian.
 */
public final class ByteBufferUtil {
  private static final Logger log = LoggerFactory.getLogger(ByteBufferUtil.class);

  // TODO: Might be clean to create a custom ByteBuffer implementation
  private ByteBufferUtil() {}

  /**
   * Resolves the charset to use in a byte array.
   *
   * @param bytes the byte array to analyze
   * @param singleByteOnly limit the detected charset to single byte encoded charsets
   * @return the detected charset, never null
   */
  private static @NotNull Charset resolveCharset(byte[] bytes, int len, boolean singleByteOnly) {
    boolean allAscii = true;
    for(int i = 0; i < len; i++) {
      if ((bytes[i] & 0x80) != 0) {
        allAscii = false;
        break;
      }
    }

    // Optimize for the by far most common case; all characters is ASCII
    if (allAscii) {
      return StandardCharsets.UTF_8;
    }

    // ChessBase probably uses ISO-8859-1, ..., ISO-8859-9 based on strings in the .exe file
    // TODO: Make it possible to disable this, or enforce it to a specific charset
    // Either using environment variables or some database config
    UniversalDetector detector = new UniversalDetector(null);
    detector.handleData(bytes, 0, bytes.length);
    detector.dataEnd();
    String detectedCharset = detector.getDetectedCharset();
    detector.reset();

    Charset charset = null;
    if (detectedCharset != null) {
      try {
        charset = Charset.forName(detectedCharset);
      } catch (Exception e) {
        // Probably shouldn't happen
        log.warn("Detector returned invalid charset!? {}", detectedCharset);
      }
    }
    if (charset == null)  {
      return singleByteOnly ? CBUtil.cbDefaultSingleByteCharset : StandardCharsets.UTF_8;
    }
    if (charset == StandardCharsets.UTF_8 && singleByteOnly) {
      charset = CBUtil.cbDefaultSingleByteCharset;
    }

    return charset;
  }

  public static ByteBuffer clone(final ByteBuffer original) {
    // Create clone with same capacity as original.
    final ByteBuffer clone = ByteBuffer.allocate(original.capacity());

    // Create a read-only copy of the original.
    // This allows reading from the original without modifying it.
    final ByteBuffer readOnlyCopy = original.asReadOnlyBuffer();

    // Flip and read from the original.
    readOnlyCopy.flip();
    clone.put(readOnlyCopy);

    return clone;
  }

  /**
   * Gets a length-encoded byte string from a {@link ByteBuffer}.
   *
   * @param buf the buffer to read from
   * @return the read string
   */
  public static String getByteString(ByteBuffer buf) {
    byte len = buf.get();
    byte[] bytes = new byte[len];
    buf.get(bytes, 0, len);
    if (len > 0 && bytes[len - 1] == 0) len--;
    return new String(bytes, 0, len, resolveCharset(bytes, len, true));
  }

  /**
   * Gets a zero-terminated byte string from a {@link ByteBuffer}.
   *
   * @param buf the buffer to read from
   * @return the read string
   */
  public static String getByteStringZeroTerminated(ByteBuffer buf) {
    int len = 0, start = buf.position();
    while (start + len < buf.limit() && buf.get(start + len) != 0) len++;
    boolean hasNullTerminator = start + len < buf.limit();
    int bytesToRead = hasNullTerminator ? len + 1 : len;
    byte[] bytes = new byte[bytesToRead];
    buf.get(bytes, 0, bytesToRead);
    return new String(bytes, 0, len, resolveCharset(bytes, len, true));
  }

  /**
   * Gets a byte encoded string with a given max length from a {@link ByteBuffer}. Exactly maxLength
   * bytes will be read from the buffer, even if the actual string is shorter (the string stops at
   * the first 0)
   *
   * @param buf the buffer to read from
   * @param maxLength the maximum length of the string
   * @return the read string
   */
  public static String getFixedSizeByteString(ByteBuffer buf, int maxLength) {
    return getFixedSizeByteString(buf, maxLength, false);
  }

  /**
   * Gets a byte encoded string with a given max length from a {@link ByteBuffer}. Exactly maxLength
   * bytes will be read from the buffer, even if the actual string is shorter (the string stops at
   * the first 0)
   *
   * @param buf the buffer to read from
   * @param maxLength the maximum length of the string
   * @param fixedWidth the charset used to decode the string must be a 1 byte fixed-width charset
   * @return the read string
   */
  public static String getFixedSizeByteString(ByteBuffer buf, int maxLength, boolean fixedWidth) {
    byte[] bytes = new byte[maxLength];
    buf.get(bytes);
    int len = 0;
    while (len < maxLength && bytes[len] != 0) len++;
    return new String(bytes, 0, len, resolveCharset(bytes, len, fixedWidth));
  }

  /**
   * Gets a byte encoded string from a {@link ByteBuffer}. The string is terminated by a new line
   * character, 0x0a
   *
   * @param buf the buffer to read from
   * @return the read string
   */
  public static String getByteStringLine(ByteBuffer buf) {
    int start = buf.position();
    while (buf.hasRemaining() && buf.get() != 10)
      ;
    byte[] bytes = new byte[buf.position() - start];
    buf.position(start);
    buf.get(bytes);

    return new String(bytes, resolveCharset(bytes, bytes.length, true));
  }

  /**
   * Puts a length encoded string to a {@link ByteBuffer}.
   *
   * @param buf the buffer to write to
   * @param s the string to put
   */
  public static void putByteString(ByteBuffer buf, String s) {
    ByteBuffer sbuf = CBUtil.cbDefaultSingleByteCharset.encode(s);
    putByte(buf, s.length());
    buf.put(sbuf);
  }

  /**
   * Puts a raw encoded string to a {@link ByteBuffer} with no length bytes or zero termination
   *
   * @param buf the buffer to write to
   * @param s the string to put
   */
  public static void putRawByteString(ByteBuffer buf, String s) {
    Charset charset = CBUtil.cbDefaultSingleByteCharset;
    buf.put(charset.encode(s));
  }

  /**
   * Puts a fixed-width string to a {@link ByteBuffer}. If the length of the string is longer than
   * the max length, it will get truncated. If it's shorter, it will be padded with zeros.
   *
   * @param buf the buffer to write to
   * @param s the string to put
   * @param length the length of the string
   */
  public static void putFixedSizeByteString(ByteBuffer buf, String s, int length) {
    putFixedSizeByteString(buf, s, length, false);
  }

  /**
   * Puts a fixed-width string to a {@link ByteBuffer}. If the length of the string is longer than
   * the max length, it will get truncated. If it's shorter, it will be padded with zeros.
   *
   * @param buf the buffer to write to
   * @param s the string to put
   * @param length the length of the string
   * @param fixedWidth if true, the encoding used must be 1 byte per character
   */
  public static void putFixedSizeByteString(ByteBuffer buf, String s, int length, boolean fixedWidth) {
    Charset charset = fixedWidth ? StandardCharsets.UTF_8 : CBUtil.cbDefaultSingleByteCharset;
    ByteBuffer sbuf = charset.encode(s);
    sbuf.position(0);
    if (sbuf.limit() > length) {
      sbuf.limit(length);
    }
    buf.put(sbuf);
    for (int i = sbuf.limit(); i < length; i++) {
      buf.put((byte) 0);
    }
  }

  public static byte getSignedByte(ByteBuffer buf) {
    return buf.get();
  }

  public static int getUnsignedByte(ByteBuffer buf) {
    return buf.get() & 0xff;
  }

  public static int getUnsignedByte(ByteBuffer buf, int offset) {
    return buf.get(buf.position() + offset) & 0xff;
  }

  public static int getUnsignedByte(byte[] buf, int offset) {
    return buf[offset] & 0xff;
  }

  public static void putByte(ByteBuffer buf, int offset, int value) {
    buf.put(offset, (byte) value);
  }

  public static void putByte(ByteBuffer buf, int value) {
    buf.put((byte) value);
  }

  // Methods for reading and writing Big Endian data from a ByteBuffer
  // (most significant byte comes first)

  public static int getUnsignedShortB(ByteBuffer buf) {
    int b1 = getUnsignedByte(buf), b2 = getUnsignedByte(buf);
    return (b1 << 8) + b2;
  }

  public static int getUnsignedShortB(byte[] buf, int offset) {
    int b1 = getUnsignedByte(buf, offset), b2 = getUnsignedByte(buf, offset + 1);
    return (b1 << 8) + b2;
  }

  public static int getUnsignedShortB(ByteBuffer buf, int offset) {
    int b1 = getUnsignedByte(buf, offset), b2 = getUnsignedByte(buf, offset + 1);
    return (b1 << 8) + b2;
  }

  public static short getSignedShortB(ByteBuffer buf) {
    return (short) getUnsignedShortB(buf);
  }

  public static int getUnsigned24BitB(ByteBuffer buf) {
    int b1 = getUnsignedByte(buf), b2 = getUnsignedByte(buf), b3 = getUnsignedByte(buf);
    return (b1 << 16) + (b2 << 8) + b3;
  }

  public static int getUnsigned24BitB(ByteBuffer buf, int offset) {
    int b1 = getUnsignedByte(buf, offset),
        b2 = getUnsignedByte(buf, offset + 1),
        b3 = getUnsignedByte(buf, offset + 2);
    return (b1 << 16) + (b2 << 8) + b3;
  }

  public static int getUnsigned24BitB(byte[] buf, int offset) {
    int b1 = getUnsignedByte(buf, offset),
        b2 = getUnsignedByte(buf, offset + 1),
        b3 = getUnsignedByte(buf, offset + 2);
    return (b1 << 16) + (b2 << 8) + b3;
  }

  public static int getSigned24BitB(ByteBuffer buf) {
    int val = getUnsigned24BitB(buf);
    if (val >= (1 << 23)) val -= (1 << 24);
    return val;
  }

  public static int getIntB(ByteBuffer buf) {
    int b1 = getUnsignedByte(buf), b2 = getUnsignedByte(buf);
    int b3 = getUnsignedByte(buf), b4 = getUnsignedByte(buf);
    return (b1 << 24) + (b2 << 16) + (b3 << 8) + b4;
  }

  public static int getIntB(ByteBuffer buf, int offset) {
    int b1 = getUnsignedByte(buf, offset), b2 = getUnsignedByte(buf, offset + 1);
    int b3 = getUnsignedByte(buf, offset + 2), b4 = getUnsignedByte(buf, offset + 3);
    return (b1 << 24) + (b2 << 16) + (b3 << 8) + b4;
  }

  public static int getIntB(byte[] buf, int offset) {
    int b1 = getUnsignedByte(buf, offset), b2 = getUnsignedByte(buf, offset + 1);
    int b3 = getUnsignedByte(buf, offset + 2), b4 = getUnsignedByte(buf, offset + 3);
    return (b1 << 24) + (b2 << 16) + (b3 << 8) + b4;
  }

  public static long getLongB(ByteBuffer buf) {
    long b1 = getUnsignedByte(buf), b2 = getUnsignedByte(buf);
    long b3 = getUnsignedByte(buf), b4 = getUnsignedByte(buf);
    long b5 = getUnsignedByte(buf), b6 = getUnsignedByte(buf);
    long b7 = getUnsignedByte(buf), b8 = getUnsignedByte(buf);
    return (b1 << 56)
        + (b2 << 48)
        + (b3 << 40)
        + (b4 << 32)
        + (b5 << 24)
        + (b6 << 16)
        + (b7 << 8)
        + b8;
  }

  public static void putShortB(ByteBuffer buf, int value) {
    buf.put((byte) (value >> 8));
    buf.put((byte) value);
  }

  public static void put24BitB(ByteBuffer buf, int value) {
    buf.put((byte) (value >> 16));
    buf.put((byte) (value >> 8));
    buf.put((byte) value);
  }

  public static void putIntB(ByteBuffer buf, int value) {
    buf.put((byte) (value >> 24));
    buf.put((byte) (value >> 16));
    buf.put((byte) (value >> 8));
    buf.put((byte) value);
  }

  public static void putLongB(ByteBuffer buf, long value) {
    buf.put((byte) (value >> 56));
    buf.put((byte) (value >> 48));
    buf.put((byte) (value >> 40));
    buf.put((byte) (value >> 32));
    buf.put((byte) (value >> 24));
    buf.put((byte) (value >> 16));
    buf.put((byte) (value >> 8));
    buf.put((byte) value);
  }

  // Methods for reading and writing Little Endian data from a ByteBuffer
  // (most significant byte comes last)

  public static int getUnsignedShortL(ByteBuffer buf) {
    int b1 = getUnsignedByte(buf), b2 = getUnsignedByte(buf);
    return (b2 << 8) + b1;
  }

  public static int getUnsignedShortL(byte[] buf, int offset) {
    int b1 = getUnsignedByte(buf, offset), b2 = getUnsignedByte(buf, offset + 1);
    return (b2 << 8) + b1;
  }

  public static short getSignedShortL(ByteBuffer buf) {
    return (short) getUnsignedShortL(buf);
  }

  public static int getUnsigned24BitL(ByteBuffer buf) {
    int b1 = getUnsignedByte(buf), b2 = getUnsignedByte(buf), b3 = getUnsignedByte(buf);
    return (b3 << 16) + (b2 << 8) + b1;
  }

  public static int getSigned24BitL(ByteBuffer buf) {
    int val = getUnsigned24BitL(buf);
    if (val >= (1 << 23)) val -= (1 << 24);
    return val;
  }

  public static int getIntL(ByteBuffer buf) {
    int b1 = getUnsignedByte(buf), b2 = getUnsignedByte(buf);
    int b3 = getUnsignedByte(buf), b4 = getUnsignedByte(buf);
    return (b4 << 24) + (b3 << 16) + (b2 << 8) + b1;
  }

  public static int getIntL(byte[] buf, int offset) {
    int b1 = getUnsignedByte(buf, offset), b2 = getUnsignedByte(buf, offset + 1);
    int b3 = getUnsignedByte(buf, offset + 2), b4 = getUnsignedByte(buf, offset + 3);
    return (b4 << 24) + (b3 << 16) + (b2 << 8) + b1;
  }

  public static long getLongL(ByteBuffer buf) {
    long b1 = getUnsignedByte(buf), b2 = getUnsignedByte(buf);
    long b3 = getUnsignedByte(buf), b4 = getUnsignedByte(buf);
    long b5 = getUnsignedByte(buf), b6 = getUnsignedByte(buf);
    long b7 = getUnsignedByte(buf), b8 = getUnsignedByte(buf);
    return (b8 << 56)
        + (b7 << 48)
        + (b6 << 40)
        + (b5 << 32)
        + (b4 << 24)
        + (b3 << 16)
        + (b2 << 8)
        + b1;
  }

  public static void putShortL(ByteBuffer buf, int value) {
    buf.put((byte) value);
    buf.put((byte) (value >> 8));
  }

  public static void put24BitL(ByteBuffer buf, int value) {
    buf.put((byte) value);
    buf.put((byte) (value >> 8));
    buf.put((byte) (value >> 16));
  }

  public static void putIntL(ByteBuffer buf, int value) {
    buf.put((byte) value);
    buf.put((byte) (value >> 8));
    buf.put((byte) (value >> 16));
    buf.put((byte) (value >> 24));
  }

  public static void putLongL(ByteBuffer buf, long value) {
    buf.put((byte) value);
    buf.put((byte) (value >> 8));
    buf.put((byte) (value >> 16));
    buf.put((byte) (value >> 24));
    buf.put((byte) (value >> 32));
    buf.put((byte) (value >> 40));
    buf.put((byte) (value >> 48));
    buf.put((byte) (value >> 56));
  }

  public static double getDoubleL(ByteBuffer buf) {
    return buf.getDouble();
  }

  public static void putDoubleL(ByteBuffer buf, double value) {
    buf.putDouble(value);
  }
}
