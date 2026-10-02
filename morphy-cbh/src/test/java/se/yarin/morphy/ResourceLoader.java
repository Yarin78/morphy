package se.yarin.morphy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

public class ResourceLoader {
  private static Path tempRoot;

  /**
   * Creates a fresh directory under a per-JVM root that is deleted recursively when the JVM exits,
   * so materialized databases don't pile up in the system temp directory.
   */
  private static synchronized Path createTempDirectory(String prefix) throws IOException {
    if (tempRoot == null) {
      Path root = Files.createTempDirectory("morphy-test-");
      Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(root)));
      tempRoot = root;
    }
    return Files.createTempDirectory(tempRoot, prefix);
  }

  private static void deleteRecursively(Path root) {
    try (Stream<Path> paths = Files.walk(root)) {
      paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    } catch (IOException | UncheckedIOException ignored) {
      // Best effort; this runs during JVM shutdown
    }
  }

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

  public static File materializeStream(String name, InputStream stream, String extension)
      throws IOException {
    Path tempFile = Files.createTempFile(createTempDirectory(name), name, extension);
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

  public static File materializeDatabaseStream(Class resourceRoot, String databaseName)
      throws IOException {
    return materializeDatabaseStream(resourceRoot, "", databaseName, DatabaseCbh.ALL_EXTENSIONS);
  }

  public static File materializeDatabaseStream(
      Class resourceRoot, String databaseName, List<String> extensions) throws IOException {
    return materializeDatabaseStream(resourceRoot, "", databaseName, extensions);
  }

  public static File materializeDatabaseStream(
      Class resourceRoot, String parentPath, String databaseName) throws IOException {
    return materializeDatabaseStream(
        resourceRoot, parentPath, databaseName, DatabaseCbh.ALL_EXTENSIONS);
  }

  public static File materializeDatabaseStream(
      Class resourceRoot, String parentPath, String databaseName, List<String> extensions)
      throws IOException {
    if (databaseName.contains("/") || databaseName.contains(".")) {
      throw new IllegalArgumentException("Database name should not contain paths or extension");
    }
    if (parentPath == null) {
      parentPath = "";
    } else if (parentPath.length() > 0 && !parentPath.endsWith("/")) {
      parentPath += "/";
    }

    File targetDirectory = createTempDirectory(databaseName).toFile();

    HashMap<String, File> extensionFiles = new HashMap<>();
    String firstMatchingExtension = null;
    for (String extension : extensions) {
      InputStream inputStream =
          resourceRoot.getResourceAsStream(parentPath + databaseName + extension);
      if (inputStream != null) {
        if (firstMatchingExtension == null) {
          firstMatchingExtension = extension;
        }
        extensionFiles.put(
            extension,
            materializeStream(inputStream, new File(targetDirectory, databaseName + extension)));
      }
    }
    if (firstMatchingExtension == null) {
      throw new IllegalArgumentException("No matching streams found");
    }
    return extensionFiles.get(firstMatchingExtension);
  }

  public static File materializeStreamPath(Class resourceRoot, String path) throws IOException {
    // Copies everything, recursively, in the given resource path to a temporary directory
    // and returns a reference to the directory

    File tempDir = createTempDirectory("resources").toFile();
    materializeStreamPathRecursively(resourceRoot, path, tempDir);
    return tempDir;
  }

  private static void materializeStreamPathRecursively(
      Class resourceRoot, String path, File tempDir) throws IOException {
    File[] files = listResourceFiles(resourceRoot, path);
    for (File file : files) {
      String relativePath =
          path == null || path.length() == 0 ? file.getName() : (path + "/" + file.getName());
      if (file.isDirectory()) {
        File dir = new File(tempDir, file.getName());
        dir.mkdir();
        materializeStreamPathRecursively(resourceRoot, relativePath, dir);
      } else if (file.isFile()) {
        InputStream stream = resourceRoot.getResourceAsStream(relativePath);
        materializeStream(stream, new File(tempDir, file.getName()));
      }
    }
  }

  private static File[] listResourceFiles(Class resourceRoot, String path) {
    String rootPath = resourceRoot.getPackageName().replace(".", "/");
    if (path != null && path.length() > 0) {
      rootPath += "/" + path;
    }
    URL url = resourceRoot.getClassLoader().getResource(rootPath);
    return new File(url.getPath()).listFiles();
  }

  public static DatabaseCbh openWorldChDatabase() {
    try {
      File file =
          ResourceLoader.materializeDatabaseStream(DatabaseCbh.class, "database/World-ch", "World-ch");
      // Can't openInMemory because the serializedFilter tests only works on persistent storage
      return DatabaseCbh.open(file);
    } catch (IOException e) {
      throw new RuntimeException("Failed to open World-ch test database");
    }
  }

  public static DatabaseCbh openWorldChDatabaseInMemory() {
    try {
      File file =
          ResourceLoader.materializeDatabaseStream(DatabaseCbh.class, "database/World-ch", "World-ch");
      return DatabaseCbh.open(file, DatabaseMode.IN_MEMORY);
    } catch (IOException e) {
      throw new RuntimeException("Failed to open World-ch test database");
    }
  }
}
