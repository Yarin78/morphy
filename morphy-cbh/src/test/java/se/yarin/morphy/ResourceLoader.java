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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ResourceLoader {
    public static ByteBuffer loadResource(String resourceName) throws IOException {
        return loadResource(ResourceLoader.class, resourceName);
    }

    public static ByteBuffer loadResource(Class<?> clazz, String resourceName) throws IOException {
        InputStream stream = clazz.getResourceAsStream(resourceName);

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

    public static File materializeDatabaseStream(Class resourceRoot, String databaseName) throws IOException {
        return materializeDatabaseStream(resourceRoot, "", databaseName, Database.ALL_EXTENSIONS);
    }

    public static File materializeDatabaseStream(Class resourceRoot, String databaseName, List<String> extensions) throws IOException {
        return materializeDatabaseStream(resourceRoot, "", databaseName, extensions);
    }

    public static File materializeDatabaseStream(Class resourceRoot, String parentPath, String databaseName) throws IOException {
        return materializeDatabaseStream(resourceRoot, parentPath, databaseName, Database.ALL_EXTENSIONS);
    }

    public static File materializeDatabaseStream(Class resourceRoot, String parentPath, String databaseName, List<String> extensions) throws IOException {
        if (databaseName.contains("/") || databaseName.contains(".")) {
            throw new IllegalArgumentException("Database name should not contain paths or extension");
        }
        if (parentPath == null) {
            parentPath = "";
        } else if (parentPath.length() > 0 && !parentPath.endsWith("/")) {
            parentPath += "/";
        }

        File targetDirectory = Files.createTempDirectory(databaseName).toFile();

        HashMap<String, File> extensionFiles = new HashMap<>();
        String firstMatchingExtension = null;
        for (String extension : extensions) {
            InputStream inputStream = resourceRoot.getResourceAsStream(parentPath + databaseName + extension);
            if (inputStream != null) {
                if (firstMatchingExtension == null) {
                    firstMatchingExtension = extension;
                }
                extensionFiles.put(extension, materializeStream(inputStream, new File(targetDirectory, databaseName + extension)));
            }
        }
        if (firstMatchingExtension == null) {
            throw new IllegalArgumentException("No matching streams found");
        }
        return extensionFiles.get(firstMatchingExtension);
    }

    public static Database openWorldChDatabase() {
        try {
            File file = ResourceLoader.materializeDatabaseStream(GameIndex.class, "World-ch", "World-ch");
            // Can't openInMemory because the serializedFilter tests only works on persistent storage
            return Database.open(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to open World-ch test database");
        }
    }

}
