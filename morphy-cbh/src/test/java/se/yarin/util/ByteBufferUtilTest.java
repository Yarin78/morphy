package se.yarin.util;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.*;

public class ByteBufferUtilTest {

  // ==================== Clone Tests ====================

  @Test
  public void testClone() {
    ByteBuffer original = ByteBuffer.allocate(10);
    original.put(new byte[] {1, 2, 3, 4, 5});
    original.flip();

    ByteBuffer clone = ByteBufferUtil.clone(original);

    assertEquals(original.capacity(), clone.capacity());
    // Original should be unchanged
    assertEquals(0, original.position());
  }

  // ==================== String Tests ====================

  @Test
  public void testGetStringZeroTerminated() {
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {'m', (byte) 229, (byte) 189, 0});
    assertEquals("må½", ByteBufferUtil.getByteStringZeroTerminated(buf));
    assertEquals(4, buf.position());
  }

  @Test
  public void testGetStringZeroTerminatedWithoutTerminator() {
    // Test the bounds check fix - buffer without null terminator
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {'a', 'b', 'c'});
    assertEquals("abc", ByteBufferUtil.getByteStringZeroTerminated(buf));
    assertEquals(3, buf.position());
  }

  @Test
  public void testGetStringZeroTerminatedEmpty() {
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {0, 'x'});
    assertEquals("", ByteBufferUtil.getByteStringZeroTerminated(buf));
    assertEquals(1, buf.position());
  }

  @Test
  public void testReadLengthByteString() {
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {3, 'a', (byte) 196, (byte) 226});
    assertEquals("aÄâ", ByteBufferUtil.getByteString(buf));
    assertEquals(4, buf.position());

    buf = ByteBuffer.wrap(new byte[] {4, 'a', (byte) 196, (byte) 226, 0});
    assertEquals("aÄâ", ByteBufferUtil.getByteString(buf));
    assertEquals(5, buf.position());

    buf = ByteBuffer.wrap(new byte[] {4, 'a', (byte) 196, (byte) 226, 0, 'b'});
    assertEquals("aÄâ", ByteBufferUtil.getByteString(buf));
    assertEquals(5, buf.position());
  }

  @Test
  public void testGetFixedSizeByteString() {
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {'h', 'e', 'l', 'l', 'o', 0, 0, 0});
    assertEquals("hello", ByteBufferUtil.getFixedSizeByteString(buf, 8));
    assertEquals(8, buf.position());

    buf = ByteBuffer.wrap(new byte[] {'h', 'i', 0, 0, 0});
    assertEquals("hi", ByteBufferUtil.getFixedSizeByteString(buf, 5));
  }

  @Test
  public void testGetByteStringLine() {
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {'l', 'i', 'n', 'e', 10, 'n', 'e', 'x', 't'});
    String line = ByteBufferUtil.getByteStringLine(buf);
    assertEquals("line\n", line);
    assertEquals(5, buf.position());
  }

  @Test
  public void testPutByteString() {
    ByteBuffer buf = ByteBuffer.allocate(10);
    ByteBufferUtil.putByteString(buf, "abc");
    buf.flip();
    assertEquals(3, buf.get()); // length byte
    assertEquals('a', buf.get());
    assertEquals('b', buf.get());
    assertEquals('c', buf.get());
  }

  @Test
  public void testPutFixedSizeByteString() {
    ByteBuffer buf = ByteBuffer.allocate(10);
    ByteBufferUtil.putFixedSizeByteString(buf, "hi", 5);
    buf.flip();
    assertEquals('h', buf.get());
    assertEquals('i', buf.get());
    assertEquals(0, buf.get()); // padding
    assertEquals(0, buf.get());
    assertEquals(0, buf.get());
  }

  @Test
  public void testPutFixedSizeByteStringTruncation() {
    ByteBuffer buf = ByteBuffer.allocate(10);
    ByteBufferUtil.putFixedSizeByteString(buf, "hello world", 5);
    buf.flip();
    assertEquals('h', buf.get());
    assertEquals('e', buf.get());
    assertEquals('l', buf.get());
    assertEquals('l', buf.get());
    assertEquals('o', buf.get());
    assertEquals(5, buf.limit());
  }

  // ==================== Byte Tests ====================

  @Test
  public void testGetSignedByte() {
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {0, 127, (byte) 128, (byte) 255});
    assertEquals(0, ByteBufferUtil.getSignedByte(buf));
    assertEquals(127, ByteBufferUtil.getSignedByte(buf));
    assertEquals(-128, ByteBufferUtil.getSignedByte(buf));
    assertEquals(-1, ByteBufferUtil.getSignedByte(buf));
  }

  @Test
  public void testGetUnsignedByte() {
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {0, 127, (byte) 128, (byte) 255});
    assertEquals(0, ByteBufferUtil.getUnsignedByte(buf));
    assertEquals(127, ByteBufferUtil.getUnsignedByte(buf));
    assertEquals(128, ByteBufferUtil.getUnsignedByte(buf));
    assertEquals(255, ByteBufferUtil.getUnsignedByte(buf));
  }

  @Test
  public void testGetUnsignedByteWithOffset() {
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {10, 20, 30, (byte) 200});
    assertEquals(10, ByteBufferUtil.getUnsignedByte(buf, 0));
    assertEquals(30, ByteBufferUtil.getUnsignedByte(buf, 2));
    assertEquals(200, ByteBufferUtil.getUnsignedByte(buf, 3));
  }

  @Test
  public void testGetUnsignedByteFromArray() {
    byte[] arr = {10, 20, (byte) 128, (byte) 255};
    assertEquals(10, ByteBufferUtil.getUnsignedByte(arr, 0));
    assertEquals(128, ByteBufferUtil.getUnsignedByte(arr, 2));
    assertEquals(255, ByteBufferUtil.getUnsignedByte(arr, 3));
  }

  @Test
  public void testPutByte() {
    ByteBuffer buf = ByteBuffer.allocate(4);
    ByteBufferUtil.putByte(buf, 0);
    ByteBufferUtil.putByte(buf, 127);
    ByteBufferUtil.putByte(buf, 128);
    ByteBufferUtil.putByte(buf, 255);
    buf.flip();
    assertEquals(0, buf.get() & 0xFF);
    assertEquals(127, buf.get() & 0xFF);
    assertEquals(128, buf.get() & 0xFF);
    assertEquals(255, buf.get() & 0xFF);
  }

  @Test
  public void testPutByteWithOffset() {
    ByteBuffer buf = ByteBuffer.allocate(4);
    ByteBufferUtil.putByte(buf, 0, 100);
    ByteBufferUtil.putByte(buf, 2, 200);
    assertEquals(100, buf.get(0) & 0xFF);
    assertEquals(200, buf.get(2) & 0xFF);
  }

  // ==================== Big Endian Short Tests ====================

  @Test
  public void testGetUnsignedShortB() {
    // Big endian: MSB first
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {0x00, 0x00}); // 0
    assertEquals(0, ByteBufferUtil.getUnsignedShortB(buf));

    buf = ByteBuffer.wrap(new byte[] {0x00, 0x01}); // 1
    assertEquals(1, ByteBufferUtil.getUnsignedShortB(buf));

    buf = ByteBuffer.wrap(new byte[] {0x01, 0x00}); // 256
    assertEquals(256, ByteBufferUtil.getUnsignedShortB(buf));

    buf = ByteBuffer.wrap(new byte[] {0x7F, (byte) 0xFF}); // 32767
    assertEquals(32767, ByteBufferUtil.getUnsignedShortB(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0x80, 0x00}); // 32768
    assertEquals(32768, ByteBufferUtil.getUnsignedShortB(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0xFF, (byte) 0xFF}); // 65535
    assertEquals(65535, ByteBufferUtil.getUnsignedShortB(buf));
  }

  @Test
  public void testGetSignedShortB() {
    // Test the fix for the empty if-statement bug
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {0x00, 0x00}); // 0
    assertEquals(0, ByteBufferUtil.getSignedShortB(buf));

    buf = ByteBuffer.wrap(new byte[] {0x00, 0x01}); // 1
    assertEquals(1, ByteBufferUtil.getSignedShortB(buf));

    buf = ByteBuffer.wrap(new byte[] {0x7F, (byte) 0xFF}); // 32767 (max positive)
    assertEquals(32767, ByteBufferUtil.getSignedShortB(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0x80, 0x00}); // -32768 (min negative)
    assertEquals(-32768, ByteBufferUtil.getSignedShortB(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0xFF, (byte) 0xFF}); // -1
    assertEquals(-1, ByteBufferUtil.getSignedShortB(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0xFF, (byte) 0xFE}); // -2
    assertEquals(-2, ByteBufferUtil.getSignedShortB(buf));
  }

  @Test
  public void testGetUnsignedShortBWithOffset() {
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {0x00, 0x01, 0x02, 0x03, 0x04});
    assertEquals(0x0102, ByteBufferUtil.getUnsignedShortB(buf, 1));
    assertEquals(0x0304, ByteBufferUtil.getUnsignedShortB(buf, 3));
  }

  @Test
  public void testGetUnsignedShortBFromArray() {
    byte[] arr = {0x01, 0x02, 0x03, 0x04};
    assertEquals(0x0102, ByteBufferUtil.getUnsignedShortB(arr, 0));
    assertEquals(0x0304, ByteBufferUtil.getUnsignedShortB(arr, 2));
  }

  @Test
  public void testPutShortB() {
    ByteBuffer buf = ByteBuffer.allocate(6);
    ByteBufferUtil.putShortB(buf, 0);
    ByteBufferUtil.putShortB(buf, 0x0102);
    ByteBufferUtil.putShortB(buf, 0xFFFF);
    buf.flip();
    assertEquals(0x00, buf.get() & 0xFF);
    assertEquals(0x00, buf.get() & 0xFF);
    assertEquals(0x01, buf.get() & 0xFF);
    assertEquals(0x02, buf.get() & 0xFF);
    assertEquals(0xFF, buf.get() & 0xFF);
    assertEquals(0xFF, buf.get() & 0xFF);
  }

  // ==================== Little Endian Short Tests ====================

  @Test
  public void testGetUnsignedShortL() {
    // Little endian: LSB first
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {0x00, 0x00}); // 0
    assertEquals(0, ByteBufferUtil.getUnsignedShortL(buf));

    buf = ByteBuffer.wrap(new byte[] {0x01, 0x00}); // 1
    assertEquals(1, ByteBufferUtil.getUnsignedShortL(buf));

    buf = ByteBuffer.wrap(new byte[] {0x00, 0x01}); // 256
    assertEquals(256, ByteBufferUtil.getUnsignedShortL(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0xFF, 0x7F}); // 32767
    assertEquals(32767, ByteBufferUtil.getUnsignedShortL(buf));

    buf = ByteBuffer.wrap(new byte[] {0x00, (byte) 0x80}); // 32768
    assertEquals(32768, ByteBufferUtil.getUnsignedShortL(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0xFF, (byte) 0xFF}); // 65535
    assertEquals(65535, ByteBufferUtil.getUnsignedShortL(buf));
  }

  @Test
  public void testGetSignedShortL() {
    // Test the fix for the empty if-statement bug
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {0x00, 0x00}); // 0
    assertEquals(0, ByteBufferUtil.getSignedShortL(buf));

    buf = ByteBuffer.wrap(new byte[] {0x01, 0x00}); // 1
    assertEquals(1, ByteBufferUtil.getSignedShortL(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0xFF, 0x7F}); // 32767 (max positive)
    assertEquals(32767, ByteBufferUtil.getSignedShortL(buf));

    buf = ByteBuffer.wrap(new byte[] {0x00, (byte) 0x80}); // -32768 (min negative)
    assertEquals(-32768, ByteBufferUtil.getSignedShortL(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0xFF, (byte) 0xFF}); // -1
    assertEquals(-1, ByteBufferUtil.getSignedShortL(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0xFE, (byte) 0xFF}); // -2
    assertEquals(-2, ByteBufferUtil.getSignedShortL(buf));
  }

  @Test
  public void testGetUnsignedShortLFromArray() {
    byte[] arr = {0x01, 0x02, 0x03, 0x04};
    assertEquals(0x0201, ByteBufferUtil.getUnsignedShortL(arr, 0));
    assertEquals(0x0403, ByteBufferUtil.getUnsignedShortL(arr, 2));
  }

  @Test
  public void testPutShortL() {
    ByteBuffer buf = ByteBuffer.allocate(6);
    ByteBufferUtil.putShortL(buf, 0);
    ByteBufferUtil.putShortL(buf, 0x0102);
    ByteBufferUtil.putShortL(buf, 0xFFFF);
    buf.flip();
    assertEquals(0x00, buf.get() & 0xFF);
    assertEquals(0x00, buf.get() & 0xFF);
    assertEquals(0x02, buf.get() & 0xFF); // LSB first
    assertEquals(0x01, buf.get() & 0xFF);
    assertEquals(0xFF, buf.get() & 0xFF);
    assertEquals(0xFF, buf.get() & 0xFF);
  }

  // ==================== Big Endian 24-bit Tests ====================

  @Test
  public void testGetUnsigned24BitB() {
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {0x00, 0x00, 0x00});
    assertEquals(0, ByteBufferUtil.getUnsigned24BitB(buf));

    buf = ByteBuffer.wrap(new byte[] {0x00, 0x00, 0x01});
    assertEquals(1, ByteBufferUtil.getUnsigned24BitB(buf));

    buf = ByteBuffer.wrap(new byte[] {0x01, 0x00, 0x00});
    assertEquals(0x010000, ByteBufferUtil.getUnsigned24BitB(buf));

    buf = ByteBuffer.wrap(new byte[] {0x7F, (byte) 0xFF, (byte) 0xFF});
    assertEquals(0x7FFFFF, ByteBufferUtil.getUnsigned24BitB(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
    assertEquals(0xFFFFFF, ByteBufferUtil.getUnsigned24BitB(buf));
  }

  @Test
  public void testGetSigned24BitB() {
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {0x00, 0x00, 0x00});
    assertEquals(0, ByteBufferUtil.getSigned24BitB(buf));

    buf = ByteBuffer.wrap(new byte[] {0x00, 0x00, 0x01});
    assertEquals(1, ByteBufferUtil.getSigned24BitB(buf));

    buf = ByteBuffer.wrap(new byte[] {0x7F, (byte) 0xFF, (byte) 0xFF}); // max positive
    assertEquals(0x7FFFFF, ByteBufferUtil.getSigned24BitB(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0x80, 0x00, 0x00}); // min negative
    assertEquals(-0x800000, ByteBufferUtil.getSigned24BitB(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF}); // -1
    assertEquals(-1, ByteBufferUtil.getSigned24BitB(buf));
  }

  @Test
  public void testGetUnsigned24BitBWithOffset() {
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {0x00, 0x01, 0x02, 0x03, 0x04});
    assertEquals(0x010203, ByteBufferUtil.getUnsigned24BitB(buf, 1));
  }

  @Test
  public void testGetUnsigned24BitBFromArray() {
    byte[] arr = {0x01, 0x02, 0x03, 0x04};
    assertEquals(0x010203, ByteBufferUtil.getUnsigned24BitB(arr, 0));
    assertEquals(0x020304, ByteBufferUtil.getUnsigned24BitB(arr, 1));
  }

  @Test
  public void testPut24BitB() {
    ByteBuffer buf = ByteBuffer.allocate(6);
    ByteBufferUtil.put24BitB(buf, 0x010203);
    ByteBufferUtil.put24BitB(buf, 0xFFFFFF);
    buf.flip();
    assertEquals(0x01, buf.get() & 0xFF);
    assertEquals(0x02, buf.get() & 0xFF);
    assertEquals(0x03, buf.get() & 0xFF);
    assertEquals(0xFF, buf.get() & 0xFF);
    assertEquals(0xFF, buf.get() & 0xFF);
    assertEquals(0xFF, buf.get() & 0xFF);
  }

  // ==================== Little Endian 24-bit Tests ====================

  @Test
  public void testGetUnsigned24BitL() {
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {0x00, 0x00, 0x00});
    assertEquals(0, ByteBufferUtil.getUnsigned24BitL(buf));

    buf = ByteBuffer.wrap(new byte[] {0x01, 0x00, 0x00});
    assertEquals(1, ByteBufferUtil.getUnsigned24BitL(buf));

    buf = ByteBuffer.wrap(new byte[] {0x00, 0x00, 0x01});
    assertEquals(0x010000, ByteBufferUtil.getUnsigned24BitL(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0xFF, (byte) 0xFF, 0x7F});
    assertEquals(0x7FFFFF, ByteBufferUtil.getUnsigned24BitL(buf));
  }

  @Test
  public void testGetSigned24BitL() {
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {0x00, 0x00, 0x00});
    assertEquals(0, ByteBufferUtil.getSigned24BitL(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF}); // -1
    assertEquals(-1, ByteBufferUtil.getSigned24BitL(buf));

    buf = ByteBuffer.wrap(new byte[] {0x00, 0x00, (byte) 0x80}); // min negative
    assertEquals(-0x800000, ByteBufferUtil.getSigned24BitL(buf));
  }

  @Test
  public void testPut24BitL() {
    ByteBuffer buf = ByteBuffer.allocate(3);
    ByteBufferUtil.put24BitL(buf, 0x010203);
    buf.flip();
    assertEquals(0x03, buf.get() & 0xFF); // LSB first
    assertEquals(0x02, buf.get() & 0xFF);
    assertEquals(0x01, buf.get() & 0xFF);
  }

  // ==================== Big Endian Int Tests ====================

  @Test
  public void testGetIntB() {
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {0x00, 0x00, 0x00, 0x00});
    assertEquals(0, ByteBufferUtil.getIntB(buf));

    buf = ByteBuffer.wrap(new byte[] {0x00, 0x00, 0x00, 0x01});
    assertEquals(1, ByteBufferUtil.getIntB(buf));

    buf = ByteBuffer.wrap(new byte[] {0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
    assertEquals(Integer.MAX_VALUE, ByteBufferUtil.getIntB(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0x80, 0x00, 0x00, 0x00});
    assertEquals(Integer.MIN_VALUE, ByteBufferUtil.getIntB(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
    assertEquals(-1, ByteBufferUtil.getIntB(buf));
  }

  @Test
  public void testGetIntBWithOffset() {
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {0x00, 0x01, 0x02, 0x03, 0x04});
    assertEquals(0x01020304, ByteBufferUtil.getIntB(buf, 1));
  }

  @Test
  public void testGetIntBFromArray() {
    byte[] arr = {0x01, 0x02, 0x03, 0x04, 0x05};
    assertEquals(0x01020304, ByteBufferUtil.getIntB(arr, 0));
    assertEquals(0x02030405, ByteBufferUtil.getIntB(arr, 1));
  }

  @Test
  public void testPutIntB() {
    ByteBuffer buf = ByteBuffer.allocate(8);
    ByteBufferUtil.putIntB(buf, 0x01020304);
    ByteBufferUtil.putIntB(buf, -1);
    buf.flip();
    assertEquals(0x01, buf.get() & 0xFF);
    assertEquals(0x02, buf.get() & 0xFF);
    assertEquals(0x03, buf.get() & 0xFF);
    assertEquals(0x04, buf.get() & 0xFF);
    assertEquals(0xFF, buf.get() & 0xFF);
    assertEquals(0xFF, buf.get() & 0xFF);
    assertEquals(0xFF, buf.get() & 0xFF);
    assertEquals(0xFF, buf.get() & 0xFF);
  }

  // ==================== Little Endian Int Tests ====================

  @Test
  public void testGetIntL() {
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {0x00, 0x00, 0x00, 0x00});
    assertEquals(0, ByteBufferUtil.getIntL(buf));

    buf = ByteBuffer.wrap(new byte[] {0x01, 0x00, 0x00, 0x00});
    assertEquals(1, ByteBufferUtil.getIntL(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x7F});
    assertEquals(Integer.MAX_VALUE, ByteBufferUtil.getIntL(buf));

    buf = ByteBuffer.wrap(new byte[] {0x00, 0x00, 0x00, (byte) 0x80});
    assertEquals(Integer.MIN_VALUE, ByteBufferUtil.getIntL(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
    assertEquals(-1, ByteBufferUtil.getIntL(buf));
  }

  @Test
  public void testGetIntLFromArray() {
    byte[] arr = {0x04, 0x03, 0x02, 0x01};
    assertEquals(0x01020304, ByteBufferUtil.getIntL(arr, 0));
  }

  @Test
  public void testPutIntL() {
    ByteBuffer buf = ByteBuffer.allocate(4);
    ByteBufferUtil.putIntL(buf, 0x01020304);
    buf.flip();
    assertEquals(0x04, buf.get() & 0xFF); // LSB first
    assertEquals(0x03, buf.get() & 0xFF);
    assertEquals(0x02, buf.get() & 0xFF);
    assertEquals(0x01, buf.get() & 0xFF);
  }

  // ==================== Big Endian Long Tests ====================

  @Test
  public void testGetLongB() {
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
    assertEquals(0L, ByteBufferUtil.getLongB(buf));

    buf = ByteBuffer.wrap(new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01});
    assertEquals(1L, ByteBufferUtil.getLongB(buf));

    buf =
        ByteBuffer.wrap(
            new byte[] {
              0x7F,
              (byte) 0xFF,
              (byte) 0xFF,
              (byte) 0xFF,
              (byte) 0xFF,
              (byte) 0xFF,
              (byte) 0xFF,
              (byte) 0xFF
            });
    assertEquals(Long.MAX_VALUE, ByteBufferUtil.getLongB(buf));

    buf =
        ByteBuffer.wrap(
            new byte[] {
              (byte) 0xFF,
              (byte) 0xFF,
              (byte) 0xFF,
              (byte) 0xFF,
              (byte) 0xFF,
              (byte) 0xFF,
              (byte) 0xFF,
              (byte) 0xFF
            });
    assertEquals(-1L, ByteBufferUtil.getLongB(buf));
  }

  @Test
  public void testPutLongB() {
    ByteBuffer buf = ByteBuffer.allocate(8);
    ByteBufferUtil.putLongB(buf, 0x0102030405060708L);
    buf.flip();
    assertEquals(0x01, buf.get() & 0xFF);
    assertEquals(0x02, buf.get() & 0xFF);
    assertEquals(0x03, buf.get() & 0xFF);
    assertEquals(0x04, buf.get() & 0xFF);
    assertEquals(0x05, buf.get() & 0xFF);
    assertEquals(0x06, buf.get() & 0xFF);
    assertEquals(0x07, buf.get() & 0xFF);
    assertEquals(0x08, buf.get() & 0xFF);
  }

  // ==================== Little Endian Long Tests ====================

  @Test
  public void testGetLongL() {
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
    assertEquals(0L, ByteBufferUtil.getLongL(buf));

    buf = ByteBuffer.wrap(new byte[] {0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
    assertEquals(1L, ByteBufferUtil.getLongL(buf));

    buf =
        ByteBuffer.wrap(
            new byte[] {
              (byte) 0xFF,
              (byte) 0xFF,
              (byte) 0xFF,
              (byte) 0xFF,
              (byte) 0xFF,
              (byte) 0xFF,
              (byte) 0xFF,
              (byte) 0xFF
            });
    assertEquals(-1L, ByteBufferUtil.getLongL(buf));
  }

  @Test
  public void testPutLongL() {
    ByteBuffer buf = ByteBuffer.allocate(8);
    ByteBufferUtil.putLongL(buf, 0x0102030405060708L);
    buf.flip();
    assertEquals(0x08, buf.get() & 0xFF); // LSB first
    assertEquals(0x07, buf.get() & 0xFF);
    assertEquals(0x06, buf.get() & 0xFF);
    assertEquals(0x05, buf.get() & 0xFF);
    assertEquals(0x04, buf.get() & 0xFF);
    assertEquals(0x03, buf.get() & 0xFF);
    assertEquals(0x02, buf.get() & 0xFF);
    assertEquals(0x01, buf.get() & 0xFF);
  }

  // ==================== Double Tests ====================

  @Test
  public void testGetDoubleL() {
    ByteBuffer buf = ByteBuffer.allocate(8);
    buf.putDouble(3.14159);
    buf.flip();
    assertEquals(3.14159, ByteBufferUtil.getDoubleL(buf), 0.00001);
  }

  @Test
  public void testPutDoubleL() {
    ByteBuffer buf = ByteBuffer.allocate(8);
    ByteBufferUtil.putDoubleL(buf, 2.71828);
    buf.flip();
    assertEquals(2.71828, buf.getDouble(), 0.00001);
  }

  // ==================== Edge Case Tests ====================

  @Test
  public void testSignedShortBoundaryValues() {
    // Specifically test the boundary around 32767/32768 where the old bug manifested
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {0x7F, (byte) 0xFE}); // 32766
    assertEquals(32766, ByteBufferUtil.getSignedShortB(buf));

    buf = ByteBuffer.wrap(new byte[] {0x7F, (byte) 0xFF}); // 32767
    assertEquals(32767, ByteBufferUtil.getSignedShortB(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0x80, 0x00}); // -32768
    assertEquals(-32768, ByteBufferUtil.getSignedShortB(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0x80, 0x01}); // -32767
    assertEquals(-32767, ByteBufferUtil.getSignedShortB(buf));
  }

  @Test
  public void testSignedShortLBoundaryValues() {
    // Same boundary tests for little endian
    ByteBuffer buf = ByteBuffer.wrap(new byte[] {(byte) 0xFE, 0x7F}); // 32766
    assertEquals(32766, ByteBufferUtil.getSignedShortL(buf));

    buf = ByteBuffer.wrap(new byte[] {(byte) 0xFF, 0x7F}); // 32767
    assertEquals(32767, ByteBufferUtil.getSignedShortL(buf));

    buf = ByteBuffer.wrap(new byte[] {0x00, (byte) 0x80}); // -32768
    assertEquals(-32768, ByteBufferUtil.getSignedShortL(buf));

    buf = ByteBuffer.wrap(new byte[] {0x01, (byte) 0x80}); // -32767
    assertEquals(-32767, ByteBufferUtil.getSignedShortL(buf));
  }
}
