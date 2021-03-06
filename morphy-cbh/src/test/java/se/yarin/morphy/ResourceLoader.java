package se.yarin.morphy;

import se.yarin.morphy.games.GameIndex;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

public class ResourceLoader {
    public static ByteBuffer loadResource(String resourceName) throws IOException {
        InputStream stream = ResourceLoader.class.getResourceAsStream(resourceName);

        int bufSize = 8192;
        byte[] bytes = new byte[bufSize];
        int len = stream.read(bytes);
        return ByteBuffer.wrap(bytes, 0, len);
    }

    public static File materializeStream(String name, InputStream stream, String extension) throws IOException {
        Path tempFile = Files.createTempFile(Files.createTempDirectory(name), name, extension);
        return materializeStream(stream, tempFile.toFile());
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
        return materializeDatabaseStream(resourceRoot, resourceNameRoot, targetDirectory, targetNameBase,
                new String[] {
                        // Database files
                        ".cbh", ".cbg", ".cba", ".cbp", ".cbt", ".cbtt", ".cbc", ".cbs", ".cbj", ".cbe", ".cbl", ".cbm",
                        // Search Boosters
                        ".cbb", ".cbgi", ".cit", ".cib", ".cit2", ".cib2"
                });
    }

    public static File materializeDatabaseStream(Class resourceRoot, String resourceNameRoot, File targetDirectory, String targetNameBase, String[] extensions) throws IOException {
        HashMap<String, File> extensionFiles = new HashMap<>();
        for (String extension : extensions) {
            InputStream inputStream = resourceRoot.getResourceAsStream(resourceNameRoot + extension);
            if (inputStream != null) {
                extensionFiles.put(extension,
                        materializeStream(inputStream,
                                new File(targetDirectory, targetNameBase + extension)));
            }
        }
        return extensionFiles.get(".cbh");
    }

    public static Database openWorldChDatabase() {
        try {
            Path worldChPath = Files.createTempDirectory("worldch");
            File file = ResourceLoader.materializeDatabaseStream(
                    GameIndex.class,
                    "World-ch/World-ch",
                    worldChPath.toFile(),
                    "World-ch");
            // Can't openInMemory because the serializedFilter tests only works on persistent storage
            return Database.open(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to open World-ch test database");
        }
    }

}
