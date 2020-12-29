package se.yarin.cbhlib;

import java.nio.ByteBuffer;

public class ByteBufferBitReader {
    private int curBit = 0;
    private int curByte = 0;
    private final ByteBuffer buffer;

    public ByteBufferBitReader(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public int getBit() {
        curBit--;
        if (curBit < 0) {
            curByte = ByteBufferUtil.getUnsignedByte(buffer);
            curBit = 7;
        }
        return (curByte & (1<<curBit)) > 0 ? 1 : 0;
    }

    public int getInt(int noBits) {
        int v = 0;
        for (int i = 0; i < noBits; i++) {
            v = v * 2 + getBit();
        }
        return v;
    }
}
