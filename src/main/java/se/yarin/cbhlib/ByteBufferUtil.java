package se.yarin.cbhlib;

import java.nio.ByteBuffer;

/**
 * Class containing static methods for getting data from a ByteBuffer
 * by explicitly telling if it is stored as little endian or big endian.
 */
public final class ByteBufferUtil {
    // TODO: Might be clean to create a custom ByteBuffer implementation
    private ByteBufferUtil() {}

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
     * @param buf the buffer to read from
     * @return the read string
     */
    public static String getByteString(ByteBuffer buf) {
        byte len = buf.get();
        byte[] bytes = new byte[len];
        buf.get(bytes, 0, len);
        if (len > 0 && bytes[len-1] == 0) len--;
        return new String(bytes, 0, len, CBUtil.cbCharSet);
    }

    /**
     * Gets a zero-terminated byte string from a {@link ByteBuffer}.
     * @param buf the buffer to read from
     * @return the read string
     */
    public static String getByteStringZeroTerminated(ByteBuffer buf) {
        int len = 0, start = buf.position();
        while (buf.get(start + len) != 0) len++;
        byte[] bytes = new byte[len+1];
        buf.get(bytes, 0, len+1);
        return new String(bytes, 0, len, CBUtil.cbCharSet);
    }

    /**
     * Gets a byte encoded string with a given max length from a {@link ByteBuffer}.
     * Exactly maxLength bytes will be read from the buffer, even if the actual
     * string is shorter (the string stops at the first 0)
     * @param buf the buffer to read from
     * @param maxLength the maximum length of the string
     * @return the read string
     */
    public static String getFixedSizeByteString(ByteBuffer buf, int maxLength) {
        byte[] bytes = new byte[maxLength];
        buf.get(bytes);
        int len = 0;
        while (len < maxLength && bytes[len] != 0) len++;
        return new String(bytes, 0, len, CBUtil.cbCharSet);
    }

    /**
     * Puts a length encoded string to a {@link ByteBuffer}.
     * @param buf the buffer to write to
     * @param s the string to put
     */
    public static void putByteString(ByteBuffer buf, String s) {
        ByteBuffer sbuf = CBUtil.cbCharSet.encode(s);
        putByte(buf, s.length());
        buf.put(sbuf);
    }

    /**
     * Puts a fixed-width string to a {@link ByteBuffer}. If the length of the string
     * is longer than the max length, it will get truncated.
     * If it's shorter, it will be padded with zeros.
     * @param buf the buffer to write to
     * @param s the string to put
     * @param length the length of the string
     */
    public static void putFixedSizeByteString(ByteBuffer buf, String s, int length) {
        ByteBuffer sbuf = CBUtil.cbCharSet.encode(s);
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

    public static void putByte(ByteBuffer buf, int value) { buf.put((byte) value); }

    // Methods for reading and writing Big Endian data from a ByteBuffer
    // (most significant byte comes first)

    public static int getUnsignedShortB(ByteBuffer buf) {
        int b1 = getUnsignedByte(buf), b2 = getUnsignedByte(buf);
        return (b1 << 8) + b2;
    }

    public static short getSignedShortB(ByteBuffer buf) {
        int val = getUnsignedShortB(buf);
        if (val >= (1 << 15));
            val -= (1 << 16);
        return (short) val;
    }

    public static int getUnsigned24BitB(ByteBuffer buf) {
        int b1 = getUnsignedByte(buf), b2 = getUnsignedByte(buf), b3 = getUnsignedByte(buf);
        return (b1 << 16) + (b2 << 8) + b3;
    }

    public static int getSigned24BitB(ByteBuffer buf) {
        int val = getUnsigned24BitB(buf);
        if (val >= (1 << 23))
            val -= (1 << 24);
        return val;
    }

    public static int getIntB(ByteBuffer buf) {
        int b1 = getUnsignedByte(buf), b2 = getUnsignedByte(buf);
        int b3 = getUnsignedByte(buf), b4 = getUnsignedByte(buf);
        return (b1 << 24) + (b2 << 16) + (b3 << 8) + b4;
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


    // Methods for reading and writing Little Endian data from a ByteBuffer
    // (most significant byte comes last)

    public static int getUnsignedShortL(ByteBuffer buf) {
        int b1 = getUnsignedByte(buf), b2 = getUnsignedByte(buf);
        return (b2 << 8) + b1;
    }

    public static short getSignedShortL(ByteBuffer buf) {
        int val = getUnsignedShortL(buf);
        if (val >= (1 << 15));
        val -= (1 << 16);
        return (short) val;
    }

    public static int getUnsigned24BitL(ByteBuffer buf) {
        int b1 = getUnsignedByte(buf), b2 = getUnsignedByte(buf), b3 = getUnsignedByte(buf);
        return (b3 << 16) + (b2 << 8) + b1;
    }

    public static int getSigned24BitL(ByteBuffer buf) {
        int val = getUnsigned24BitL(buf);
        if (val >= (1 << 23))
            val -= (1 << 24);
        return val;
    }

    public static int getIntL(ByteBuffer buf) {
        int b1 = getUnsignedByte(buf), b2 = getUnsignedByte(buf);
        int b3 = getUnsignedByte(buf), b4 = getUnsignedByte(buf);
        return (b4 << 24) + (b3 << 16) + (b2 << 8) + b1;
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
}
