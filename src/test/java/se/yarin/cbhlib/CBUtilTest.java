package se.yarin.cbhlib;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

public class CBUtilTest {
    @Test
    public void testGetStringZeroTerminated() {
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{'m', (byte) 229, (byte) 189, 0});
        assertEquals("må½", CBUtil.getByteStringZeroTerminated(buf));
        assertEquals(4, buf.position());
    }

    @Test
    public void testReadLengthByteString() {
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{3, 'a', (byte) 196, (byte) 226});
        assertEquals("aÄâ", CBUtil.getByteString(buf));
        assertEquals(4, buf.position());

        buf = ByteBuffer.wrap(new byte[]{4, 'a', (byte) 196, (byte) 226, 0});
        assertEquals("aÄâ", CBUtil.getByteString(buf));
        assertEquals(5, buf.position());

        buf = ByteBuffer.wrap(new byte[]{4, 'a', (byte) 196, (byte) 226, 0, 'b'});
        assertEquals("aÄâ", CBUtil.getByteString(buf));
        assertEquals(5, buf.position());
    }
}
