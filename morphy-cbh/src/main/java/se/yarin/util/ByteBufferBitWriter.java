package se.yarin.util;

import java.nio.ByteBuffer;

public class ByteBufferBitWriter implements AutoCloseable {
    private int curBit = 7;
    private int curByte = 0;
    private final ByteBuffer buffer;

    public ByteBufferBitWriter(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public void putBit(int bit) {
        if (bit == 1) curByte |= (1 << curBit);
        curBit--;
        if (curBit < 0) {
            flushCurrent();
        }
    }

    public void putBits(int value, int noBits) {
        for (int i = noBits - 1; i >= 0; i--) {
            putBit((value >> i) & 1);
        }
    }

    public void flushCurrent() {
        buffer.put((byte) curByte);
        curByte = 0;
        curBit = 7;
    }

    @Override
    public void close() {
        if (curBit != 7) {
            flushCurrent();
        }
    }
}
