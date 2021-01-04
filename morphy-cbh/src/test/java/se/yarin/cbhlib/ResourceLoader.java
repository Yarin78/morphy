package se.yarin.cbhlib;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class ResourceLoader {
    public static ByteBuffer loadResource(String resourceName) throws IOException {
        InputStream stream = ResourceLoader.class.getResourceAsStream(resourceName);

        int bufSize = 8192;
        byte[] bytes = new byte[bufSize];
        int len = stream.read(bytes);
        return ByteBuffer.wrap(bytes, 0, len);
    }
}
