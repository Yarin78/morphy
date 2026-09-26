package se.yarin.morphy.tools.testdata;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.api.Database;
import se.yarin.morphy.api.Databases;
import se.yarin.morphy.tools.testdata.Versions.Version;

/**
 * Generates a series of test databases, in each of the formats, meant to be opened in ChessBase.
 *
 * <p>The databases are built in versions. The first is simple; each later version is a copy of the
 * one before it with more done to it, and is kept as a database of its own. If ChessBase can open
 * one version and not the next, what that version added is the place to look, and the {@code
 * MANIFEST.md} in its directory lists it.
 *
 * <p>Everything is done through the neutral {@link Database} facade, so all formats get the same
 * operations. After every version the database is checked against what was put into it, first as it
 * was left and then reopened from disk.
 *
 * <p>Usage: {@code GenerateTestDatabases [outDir] [format ...]} where a format is {@code cbh},
 * {@code 2cbh} or {@code pgn}. The default is {@code test-databases/generated} and all formats.
 * The content is deterministic, though a format may put timestamps in what it writes.
 */
public class GenerateTestDatabases {

  public static void main(String[] args) throws IOException {
    Path out = Path.of(args.length > 0 ? args[0] : "test-databases/generated");
    List<Format> formats = new ArrayList<>();
    for (int i = 1; i < args.length; i++) {
      formats.add(format(args[i]));
    }
    if (formats.isEmpty()) {
      formats.addAll(List.of(Format.values()));
    }

    boolean failed = false;
    for (Format format : formats) {
      try {
        generate(out, format);
      } catch (RuntimeException e) {
        failed = true;
        System.err.println("FAILED " + format.dir + ": " + e.getMessage());
        e.printStackTrace();
      }
    }
    if (failed) {
      System.exit(1);
    }
  }

  private static Format format(String name) {
    for (Format format : Format.values()) {
      if (format.dir.equalsIgnoreCase(name)) {
        return format;
      }
    }
    throw new IllegalArgumentException("Unknown format: " + name + " (cbh, 2cbh or pgn)");
  }

  private static void generate(Path out, Format format) throws IOException {
    State state = new State();
    Path previous = null;
    for (Version version : Versions.all()) {
      Path dir = out.resolve(format.dir).resolve(version.key());
      clear(dir);
      Files.createDirectories(dir);
      File file = dir.resolve(version.key() + "." + format.extension).toFile();

      Session session;
      Database db;
      if (previous == null) {
        db = Databases.create(file);
      } else {
        copyFiles(previous, dir, version.key());
        db = Databases.open(file, AccessMode.READ_WRITE);
      }
      List<String> manifest;
      try (db) {
        session = new Session(format, db, state);
        version.apply().accept(session);
        session.verify(version.key() + " after writing");
        manifest = session.manifest;
      }
      try (Database reopened = Databases.open(file, AccessMode.READ_ONLY)) {
        new Session(format, reopened, state).verify(version.key() + " after reopening");
      }

      writeManifest(dir, format, version, state.expected.size(), manifest);
      System.out.println(
          format.dir + "/" + version.key() + ": " + state.expected.size() + " records, verified");
      previous = file.toPath();
    }
  }

  /** Copies every file of the previous version, which are the ones with its name. */
  private static void copyFiles(Path previous, Path dir, String base) throws IOException {
    String prevName = previous.getFileName().toString();
    String prevBase = prevName.substring(0, prevName.indexOf('.'));
    try (Stream<Path> files = Files.list(previous.getParent())) {
      for (Path file : (Iterable<Path>) files::iterator) {
        String name = file.getFileName().toString();
        if (name.startsWith(prevBase + ".")) {
          Files.copy(
              file,
              dir.resolve(base + name.substring(prevBase.length())),
              StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  /** Empties a version's directory, which only ever holds what this tool wrote. */
  private static void clear(Path dir) throws IOException {
    if (!Files.isDirectory(dir)) {
      return;
    }
    try (Stream<Path> files = Files.list(dir)) {
      for (Path file : (Iterable<Path>) files::iterator) {
        if (Files.isRegularFile(file)) {
          Files.delete(file);
        }
      }
    }
  }

  private static void writeManifest(
      Path dir, Format format, Version version, int games, List<String> manifest)
      throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("# ").append(version.key()).append(" (").append(format.dir).append(")\n\n");
    sb.append(version.title()).append(". ").append(games).append(" games in total.\n\n");
    sb.append("What this version added to the one before it:\n\n");
    for (String line : manifest) {
      sb.append("- ").append(line).append('\n');
    }
    Files.writeString(dir.resolve("MANIFEST.md"), sb.toString());
  }
}
