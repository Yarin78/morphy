package se.yarin.morphy.positions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.NotNull;
import se.yarin.chess.Player;
import se.yarin.chess.Position;

/**
 * An open position index: its manifest, the facts of its games, and its segments, whose keys,
 * offsets and directories are in memory (some 650 MB for a Megabase in one segment); the rest is
 * read from the files as positions are looked up. A position is looked up in every segment, and
 * what they have of it joined. Lookups can be made from several threads at once.
 */
public final class PositionIndex implements AutoCloseable {
  private final @NotNull Path directory;
  private final @NotNull IndexMeta meta;
  private final @NotNull GameFactsTable facts;
  private final @NotNull List<SegmentReader> segments;
  private final @NotNull SupersededGames superseded;

  private PositionIndex(
      Path directory, IndexMeta meta, GameFactsTable facts, List<SegmentReader> segments) {
    this.directory = directory;
    this.meta = meta;
    this.facts = facts;
    this.segments = segments;
    this.superseded =
        new SupersededGames(segments.stream().map(SegmentReader::supersedes).toList(), facts.maxId());
  }

  /**
   * Opens an index.
   *
   * @throws IOException if there is no index in the directory, or it can't be read
   */
  public static @NotNull PositionIndex open(@NotNull Path directory) throws IOException {
    IndexMeta meta = IndexMeta.read(directory);
    GameFactsTable facts = GameFactsTable.read(directory.resolve(meta.facts()));
    List<SegmentReader> segments = new ArrayList<>();
    try {
      for (String segment : meta.segments()) {
        segments.add(SegmentReader.open(directory.resolve(segment)));
      }
    } catch (IOException | RuntimeException e) {
      for (SegmentReader s : segments) {
        s.close();
      }
      throw e;
    }
    return new PositionIndex(directory, meta, facts, List.copyOf(segments));
  }

  /**
   * What an index holds and was built from, without opening it.
   *
   * @throws IOException if there is no index in the directory, or it can't be read
   */
  public static @NotNull IndexMeta readMeta(@NotNull Path directory) throws IOException {
    return IndexMeta.read(directory);
  }

  public @NotNull Path directory() {
    return directory;
  }

  public @NotNull IndexMeta meta() {
    return meta;
  }

  public @NotNull GameFactsTable facts() {
    return facts;
  }

  @NotNull
  List<SegmentReader> segments() {
    return segments;
  }

  @NotNull
  SupersededGames superseded() {
    return superseded;
  }

  /**
   * Whether the index is out of date for a database and filter: the database has changed since it
   * was built or last updated, or it was built with another filter.
   */
  public boolean isStale(@NotNull DatabaseIdentity database, @NotNull String filter) {
    return !meta.database().equals(database) || !meta.filter().strip().equals(filter.strip());
  }

  /** Finds the games that reached a position, and the moves they played from it. */
  public @NotNull PositionGames find(@NotNull Position position) {
    boolean whiteToMove = position.playerToMove() == Player.WHITE;
    return PositionGames.of(
        position, groups(position.getZobristHashLo(), whiteToMove), facts, meta.recentSince());
  }

  /**
   * The games of a position by the move they played, from every segment: the games whose entries
   * count, joined by move, with the statistics the segments store added up.
   */
  @NotNull
  List<MoveGroup> groups(long hash, boolean whiteToMove) {
    Map<Integer, List<MoveGroup>> byMove = new TreeMap<>();
    for (int s = 0; s < segments.size(); s++) {
      PositionEntry entry = segments.get(s).lookup(hash);
      if (entry == null) {
        continue;
      }
      for (MoveGroup group : entry.groups()) {
        MoveGroup part = group;
        if (!superseded.none()) {
          int segment = s;
          int[] ids =
              Arrays.stream(group.gameIds()).filter(id -> superseded.counts(segment, id)).toArray();
          // Stored statistics count games that no longer do
          part = ids.length == group.gameIds().length ? group : new MoveGroup(group.moveCode(), ids, null);
        }
        if (part.gameIds().length > 0) {
          byMove.computeIfAbsent(part.moveCode(), m -> new ArrayList<>()).add(part);
        }
      }
    }
    List<MoveGroup> groups = new ArrayList<>(byMove.size());
    byMove.forEach((move, parts) -> groups.add(join(move, parts, whiteToMove)));
    return groups;
  }

  /** The parts of a move's games from several segments as one. */
  private MoveGroup join(int move, List<MoveGroup> parts, boolean whiteToMove) {
    if (parts.size() == 1) {
      return parts.getFirst();
    }
    int[] ids = parts.stream().map(MoveGroup::gameIds).flatMapToInt(Arrays::stream).sorted().toArray();
    MoveStats stats = null;
    if (parts.stream().anyMatch(p -> p.stats() != null)) {
      // Those stored added up, and the others worked out
      for (MoveGroup part : parts) {
        MoveStats s =
            part.stats() != null
                ? part.stats()
                : MoveStats.of(part.gameIds(), facts, whiteToMove, meta.recentSince());
        stats = stats == null ? s : stats.plus(s);
      }
    }
    return new MoveGroup(move, ids, stats);
  }

  @Override
  public void close() throws IOException {
    IOException failure = null;
    for (SegmentReader segment : segments) {
      try {
        segment.close();
      } catch (IOException e) {
        failure = e;
      }
    }
    if (failure != null) {
      throw failure;
    }
  }
}
