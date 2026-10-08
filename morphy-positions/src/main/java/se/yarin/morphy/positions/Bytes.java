package se.yarin.morphy.positions;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

/** A growable byte array a record is written into, and the variable-length numbers in it. */
final class Bytes {
  private byte[] bytes = new byte[256];
  private int size;

  void clear() {
    size = 0;
  }

  int size() {
    return size;
  }

  byte @NotNull [] array() {
    return bytes;
  }

  private void ensure(int more) {
    if (size + more > bytes.length) {
      bytes = Arrays.copyOf(bytes, Math.max(bytes.length * 2, size + more));
    }
  }

  void put(int b) {
    ensure(1);
    bytes[size++] = (byte) b;
  }

  void putShort(int s) {
    put(s >>> 8);
    put(s);
  }

  /** An unsigned number, 7 bits a byte, the lowest first, the high bit set on all but the last. */
  void putVar(long value) {
    ensure(10);
    while ((value & ~0x7FL) != 0) {
      bytes[size++] = (byte) ((value & 0x7F) | 0x80);
      value >>>= 7;
    }
    bytes[size++] = (byte) value;
  }

  static long getVar(@NotNull ByteBuffer buf) {
    long value = 0;
    for (int shift = 0; ; shift += 7) {
      byte b = buf.get();
      value |= (long) (b & 0x7F) << shift;
      if (b >= 0) {
        return value;
      }
    }
  }
}
