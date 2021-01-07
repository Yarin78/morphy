package se.yarin.cbhlib;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class ResourceLoader {
    public static ByteBuffer loadResource(String resourceName) throws IOException {
        InputStream stream = ResourceLoader.class.getResourceAsStream(resourceName);

        int bufSize = 8192;
        byte[] bytes = new byte[bufSize];
        int len = stream.read(bytes);
        return ByteBuffer.wrap(bytes, 0, len);
    }

    public static File materializeStream(InputStream stream, File file) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        byte[] buf = new byte[0x1000];
        while (true) {
            int r = stream.read(buf);
            if (r == -1) {
                break;
            }
            fos.write(buf, 0, r);
        }
        fos.close();
        return file;
    }

    public static File materializeDatabaseStream(Class resourceRoot, String resourceNameRoot, File targetDirectory, String targetNameBase) throws IOException {
        // TODO: Make Database.openInMemory work directly with streams (BaseLocator/BaseLoader pattern?)
        String[] extensions = {".cbh", ".cbg", ".cba", ".cbp", ".cbt", ".cbc", ".cbs", ".cbj"};
        HashMap<String, File> extensionFiles = new HashMap<>();
        for (String extension : extensions) {
            extensionFiles.put(extension,
                    materializeStream(resourceRoot.getResourceAsStream(resourceNameRoot + extension),
                        new File(targetDirectory, targetNameBase + extension)));
        }
        return extensionFiles.get(".cbh");
    }
}
