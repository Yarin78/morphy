package se.yarin.morphy.positions;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;

/**
 * An index's manifest, kept in {@code index.properties}: what it was built from, the file of its
 * games' facts, and its segments. Writing a new manifest is what makes a build, an update or a
 * compaction take effect: the files it names are complete before it's moved in place.
 *
 * @param formatVersion the version of the index files' layout
 * @param builtAt when it was built from scratch
 * @param updatedAt when games were last added, or it was built
 * @param database the database as it was when the index was last built or updated
 * @param filter the games indexed, in the database's game filter language; blank for every game
 * @param recentSince the first year of the games counted as recent in the statistics: two years
 *     before the newest when the index was built or compacted
 * @param games the games indexed
 * @param baseGames the games of the first segment, as built or compacted: the others are in the
 *     segments of updates
 * @param lastGameId the highest game id of the database when the index was last built or updated;
 *     the games after it aren't indexed
 * @param facts the file of the games' facts, in the index directory
 * @param segments the segments' directories, in the index directory, the oldest first
 * @param sharedPositions the positions several games reached, summed over the segments
 * @param singlePositions the positions one game reached, summed over the segments
 */
public record IndexMeta(
    int formatVersion,
    @NotNull Instant builtAt,
    @NotNull Instant updatedAt,
    @NotNull DatabaseIdentity database,
    @NotNull String filter,
    int recentSince,
    long games,
    long baseGames,
    int lastGameId,
    @NotNull String facts,
    @NotNull List<String> segments,
    long sharedPositions,
    long singlePositions) {

  public static final int FORMAT_VERSION = 2;

  /** Writes the manifest into an index directory, replacing the one there at once. */
  void write(@NotNull Path indexDir) throws IOException {
    Properties p = new Properties();
    p.setProperty("formatVersion", String.valueOf(formatVersion));
    p.setProperty("builtAt", builtAt.toString());
    p.setProperty("updatedAt", updatedAt.toString());
    p.setProperty("database.size", String.valueOf(database.size()));
    p.setProperty("database.modified", String.valueOf(database.modified()));
    p.setProperty("database.gameCount", String.valueOf(database.gameCount()));
    p.setProperty("filter", filter);
    p.setProperty("recentSince", String.valueOf(recentSince));
    p.setProperty("games", String.valueOf(games));
    p.setProperty("baseGames", String.valueOf(baseGames));
    p.setProperty("lastGameId", String.valueOf(lastGameId));
    p.setProperty("facts", facts);
    p.setProperty("segments", String.join(",", segments));
    p.setProperty("sharedPositions", String.valueOf(sharedPositions));
    p.setProperty("singlePositions", String.valueOf(singlePositions));
    Path temporary = indexDir.resolve(IndexFiles.MANIFEST + ".new");
    try (Writer out = Files.newBufferedWriter(temporary)) {
      p.store(out, "Morphy position index");
    }
    Files.move(
        temporary,
        indexDir.resolve(IndexFiles.MANIFEST),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE);
  }

  /** Reads the manifest of an index directory. */
  static @NotNull IndexMeta read(@NotNull Path indexDir) throws IOException {
    Path file = indexDir.resolve(IndexFiles.MANIFEST);
    Properties p = new Properties();
    try (Reader in = Files.newBufferedReader(file)) {
      p.load(in);
    }
    int version = Integer.parseInt(p.getProperty("formatVersion", "0"));
    if (version != FORMAT_VERSION) {
      throw new IOException(
          "The index in " + indexDir + " has format " + version + ", not " + FORMAT_VERSION);
    }
    String segments = p.getProperty("segments", "");
    return new IndexMeta(
        version,
        Instant.parse(p.getProperty("builtAt")),
        Instant.parse(p.getProperty("updatedAt")),
        new DatabaseIdentity(
            Long.parseLong(p.getProperty("database.size")),
            Long.parseLong(p.getProperty("database.modified")),
            Long.parseLong(p.getProperty("database.gameCount"))),
        p.getProperty("filter", ""),
        Integer.parseInt(p.getProperty("recentSince")),
        Long.parseLong(p.getProperty("games")),
        Long.parseLong(p.getProperty("baseGames")),
        Integer.parseInt(p.getProperty("lastGameId")),
        p.getProperty("facts"),
        segments.isEmpty() ? List.of() : Arrays.asList(segments.split(",")),
        Long.parseLong(p.getProperty("sharedPositions")),
        Long.parseLong(p.getProperty("singlePositions")));
  }
}
