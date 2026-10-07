package se.yarin.morphy.positions;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;

/**
 * What an index was built from and how: kept in {@code meta.properties}.
 *
 * @param formatVersion the version of the index files' layout
 * @param builtAt when it was built
 * @param database the database it was built from
 * @param recentSince the first year of the games counted as recent: two years before the newest
 * @param statsThreshold the fewest games of a move the statistics are stored for
 * @param gameIdBytes the bytes of a game id in {@code single.data}
 * @param sharedPositions the positions several games reached
 * @param singlePositions the positions one game reached
 * @param filter the games indexed, in the database's game filter language; blank for every game
 * @param games the games indexed
 */
public record IndexMeta(
    int formatVersion,
    @NotNull Instant builtAt,
    @NotNull DatabaseIdentity database,
    int recentSince,
    int statsThreshold,
    int gameIdBytes,
    long sharedPositions,
    long singlePositions,
    @NotNull String filter,
    long games) {

  public static final int FORMAT_VERSION = 1;

  void write(@NotNull Path file) throws IOException {
    Properties p = new Properties();
    p.setProperty("formatVersion", String.valueOf(formatVersion));
    p.setProperty("builtAt", builtAt.toString());
    p.setProperty("database.size", String.valueOf(database.size()));
    p.setProperty("database.modified", String.valueOf(database.modified()));
    p.setProperty("database.gameCount", String.valueOf(database.gameCount()));
    p.setProperty("recentSince", String.valueOf(recentSince));
    p.setProperty("statsThreshold", String.valueOf(statsThreshold));
    p.setProperty("gameIdBytes", String.valueOf(gameIdBytes));
    p.setProperty("sharedPositions", String.valueOf(sharedPositions));
    p.setProperty("singlePositions", String.valueOf(singlePositions));
    p.setProperty("filter", filter);
    p.setProperty("games", String.valueOf(games));
    try (Writer out = Files.newBufferedWriter(file)) {
      p.store(out, "Morphy position index");
    }
  }

  static @NotNull IndexMeta read(@NotNull Path file) throws IOException {
    Properties p = new Properties();
    try (Reader in = Files.newBufferedReader(file)) {
      p.load(in);
    }
    int version = Integer.parseInt(p.getProperty("formatVersion", "0"));
    if (version != FORMAT_VERSION) {
      throw new IOException(
          "The index in " + file.getParent() + " has format " + version + ", not " + FORMAT_VERSION);
    }
    return new IndexMeta(
        version,
        Instant.parse(p.getProperty("builtAt")),
        new DatabaseIdentity(
            Long.parseLong(p.getProperty("database.size")),
            Long.parseLong(p.getProperty("database.modified")),
            Long.parseLong(p.getProperty("database.gameCount"))),
        Integer.parseInt(p.getProperty("recentSince")),
        Integer.parseInt(p.getProperty("statsThreshold")),
        Integer.parseInt(p.getProperty("gameIdBytes")),
        Long.parseLong(p.getProperty("sharedPositions")),
        Long.parseLong(p.getProperty("singlePositions")),
        p.getProperty("filter", ""),
        Long.parseLong(p.getProperty("games", "0")));
  }
}
