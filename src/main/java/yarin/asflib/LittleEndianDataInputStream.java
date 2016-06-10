package yarin.asflib;

import java.io.*;

/**
 * This class works exactly like java.io.DataInputStream
 * except all input is treated as little endian
 */
public class LittleEndianDataInputStream extends InputStream implements DataInput {

    private DataInputStream dis;

    public LittleEndianDataInputStream(InputStream in) {
        dis = new DataInputStream(in);
    }

    @Override
    public int read() throws IOException {
        return dis.read();
    }

    public final int read(byte b[]) throws IOException {
        return dis.read(b, 0, b.length);
    }

    public final int read(byte b[], int off, int len) throws IOException {
        return dis.read(b, off, len);
    }

    public final void readFully(byte b[]) throws IOException {
        dis.readFully(b, 0, b.length);
    }

    public final void readFully(byte b[], int off, int len) throws IOException {
        dis.readFully(b, off, len);
    }

    public final int skipBytes(int n) throws IOException {
        return dis.skipBytes(n);
    }

    public final boolean readBoolean() throws IOException {
        return dis.readBoolean();
    }

    public final byte readByte() throws IOException {
        return dis.readByte();
    }

    public final int readUnsignedByte() throws IOException {
        return dis.readUnsignedByte();
    }

    public final short readShort() throws IOException {
        int ch1 = dis.read();
        int ch2 = dis.read();
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        return (short)((ch1 << 0) + (ch2 << 8));
    }

    public final int readUnsignedShort() throws IOException {
        int ch1 = dis.read();
        int ch2 = dis.read();
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        return (ch1 << 0) + (ch2 << 8);
    }

    public final char readChar() throws IOException {
        int ch1 = dis.read();
        int ch2 = dis.read();
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        return (char)((ch1 << 0) + (ch2 << 8));
    }

    public final int readInt() throws IOException {
        int ch1 = dis.read();
        int ch2 = dis.read();
        int ch3 = dis.read();
        int ch4 = dis.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0)
            throw new EOFException();
        return ((ch1 << 0) + (ch2 << 8) + (ch3 << 16) + (ch4 << 24));
    }

    private byte readBuffer[] = new byte[8];

    public final long readLong() throws IOException {
        readFully(readBuffer, 0, 8);
        return (((readBuffer[0] & 255) << 0) +
                ((readBuffer[1] & 255) << 8) +
                ((readBuffer[2] & 255) << 16) +
                ((long)(readBuffer[3] & 255) << 24) +
                ((long)(readBuffer[4] & 255) << 32) +
                ((long)(readBuffer[5] & 255) << 40) +
                ((long)(readBuffer[6] & 255) << 48) +
                ((long)(readBuffer[7]) << 56));
    }

    public final float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    public final double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    @Deprecated
    public String readLine() throws IOException {
        return dis.readLine();
    }

    public final String readUTF() throws IOException {
        return dis.readUTF(this);
    }
}
