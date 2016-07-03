package se.yarin.cbhlib;

import java.nio.ByteBuffer;

/**
 * Class containing static methods for getting data from a ByteBuffer
 * by explicitly telling if it is stored as little endian or big endian.
 */
public final class ByteBufferUtil {
    // TODO: Might be clean to create a custom ByteBuffer implementation
    private ByteBufferUtil() {}

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
        return getByteStringZeroTerminated(buf, Integer.MAX_VALUE);
    }

    /**
     * Gets a zero-terminated, max-length, byte string from a {@link ByteBuffer}.
     * @param buf the buffer to read from
     * @param maxLength the maximum length of the string
     * @return the read string
     */
    public static String getByteStringZeroTerminated(ByteBuffer buf, int maxLength) {
        // TODO: Check why we need maxLength here!?
        int len = 0, start = buf.position();
        while (len < maxLength && buf.get(start + len) != 0) len++;
        byte[] bytes = new byte[len+1];
        buf.get(bytes, 0, len+1);
        return new String(bytes, 0, len, CBUtil.cbCharSet);
    }

    public static byte getSignedByte(ByteBuffer buf) {
        return buf.get();
    }

    public static int getUnsignedByte(ByteBuffer buf) {
        return buf.get() & 0xff;
    }

    // Methods for reading Big Endian data from a ByteBuffer
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


    // Methods for reading Little Endian data from a ByteBuffer
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
}
