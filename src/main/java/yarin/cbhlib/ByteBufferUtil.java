package yarin.cbhlib;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * Contains helper methods for binary streams
 */
public class ByteBufferUtil {
    private ByteBufferUtil() {
    }

    public static int getUnsignedByte(ByteBuffer buf) {
        return buf.get() & 0xff;
    }

    public static int getUnsignedByte(ByteBuffer buf, int offset) {
        return buf.get(offset) & 0xff;
    }

    public static int getUnsignedShort(ByteBuffer buf) {
        return buf.getShort() & 0xffff;
    }

    public static int getUnsignedShort(ByteBuffer buf, int offset) {
        return buf.getShort(offset) & 0xffff;
    }

    public static int getSignedBigEndian24BitValue(ByteBuffer buf) {
        int b1 = getUnsignedByte(buf), b2 = getUnsignedByte(buf), b3 = getUnsignedByte(buf);
        int val = b1 * 256 * 256 + b2 * 256 + b3;
        if (val >= 128 * 256 * 256)
            val -= 256 * 256 * 256;
        return val;
    }

    public static int getLittleEndian24BitValue(ByteBuffer buf) {
        int b1 = getUnsignedByte(buf), b2 = getUnsignedByte(buf), b3 = getUnsignedByte(buf);
        int val = b3 * 256 * 256 + b2 * 256 + b1;
        if (val >= 128 * 256 * 256)
            val -= 256 * 256 * 256;
        return val;
    }

    public static int getLittleEndian24BitValue(ByteBuffer buf, int offset) {
        return
                (getUnsignedByte(buf, offset+2)) * 256 * 256 +
                        (getUnsignedByte(buf, offset+1)) * 256 +
                        (getUnsignedByte(buf, offset));
    }

    public static int getBigEndian24BitValue(ByteBuffer buf, int offset) {
        return
                (getUnsignedByte(buf, offset)) * 256 * 256 +
                        (getUnsignedByte(buf, offset+1)) * 256 +
                        (getUnsignedByte(buf, offset+2));
    }

    public static int getBigEndianValue(ByteBuffer buf, int offset, int startBit, int noBits) {
        if (offset >= buf.limit())
            throw new IllegalArgumentException("Offset out of range");

        int value = 0, pos = offset, curBit = startBit;
        for (int i = 0; i < noBits; i++) {
            if (pos < 0)
                throw new IllegalArgumentException("Offset out of range");
            if ((buf.get(pos) & (1 << curBit)) > 0)
                value |= 1 << i;
            curBit++;
            if (curBit == 8) {
                curBit = 0;
                pos--;
            }
        }
        return value;
    }

    public static String getZeroTerminatedString(ByteBuffer buf, int maxLength) {
        StringBuilder sb = new StringBuilder();
        if (maxLength < 0)
            maxLength = Integer.MAX_VALUE;
        for (int i = 0; i < maxLength; i++) {
            byte b = buf.get();
            if (b == 0)
                break;
            sb.append((char) b);
        }
        return sb.toString();
    }

    public static String getZeroTerminatedString(ByteBuffer buf, int offset, int maxLength) {
        int len = 0;
        while (len < maxLength && buf.get(offset + len) != 0) len++;
        return new String(buf.array(), offset, len, Charset.forName("ISO-8859-1"));
    }

    private static String[][] romanDigits =
            new String[][]{{"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"},
                    {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"},
                    {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"},
                    {"", "M", "MM", "MMM", "MMMM", "MMMMM", "MMMMMM", "MMMMMMM", "MMMMMMMM", "MMMMMMMMM"}};

    public static String getRomanNumber(int number) {
        // TODO: Move somewhere else
        String s = "";
        int dig = 0;
        while (number > 0 && dig < 4) {
            s = romanDigits[dig][number % 10] + s;
            number /= 10;
            dig++;
        }
        return s;
    }
}
