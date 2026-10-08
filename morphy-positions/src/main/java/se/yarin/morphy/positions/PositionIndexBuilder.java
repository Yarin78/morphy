package se.yarin.morphy.positions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;
import se.yarin.chess.MoveCode;
import se.yarin.morphy.api.GameScan;
import se.yarin.morphy.api.MainLine;
import se.yarin.morphy.api.ScannedGame;

/**
 * Builds, updates and compacts position indexes, from a {@link GameScan}.
 *
 * <p>The games' main lines are played through in parallel, and each position becomes a record of
 * its hash, the game, the side to move and the move played from it, collected in memory. A
 * position repeated in a game counts once, with the move first played from it. When as many
 * records are collected as fit, they're sorted by hash and written out as a segment (see {@link
 * SegmentWriter}). A build merges the segments of its batches into one; an update adds the
 * segment of the games added since; a compaction merges an index's segments into one again.
 *
 * <p>Every change is made in new files, and takes effect when the manifest naming them is written
 * (see {@link IndexMeta}). A build is made in a directory next to the index, moved in place when
 * complete; its batches need about the room of the index itself.
 */
public final class PositionIndexBuilder {
  /** An update compacts the index when it has more segments than this. */
  static final int MAX_SEGMENTS = 4;

  /** An update compacts the index when its newer segments hold more than this share of the games. */
  static final double MAX_NEWER_SHARE = 0.1;

  // The records of a thread passed on at a time
  private static final int CHUNK = 4096;

  private final @NotNull Consumer<String> progress;
  // The records collected before they're written out: some 32 bytes each, sorted
  private long batchRecords =
      Math.max(1 << 16, Math.min(64L << 20, Runtime.getRuntime().maxMemory() / 48));

  /** @param progress told how the work goes, every few seconds */
  public PositionIndexBuilder(@NotNull Consumer<String> progress) {
    this.progress = progress;
  }

  /** Collects at most this many records at a time; for testing the merging of batches. */
  PositionIndexBuilder batchRecords(long records) {
    this.batchRecords = records;
    return this;
  }

  // ---------------------------------------------------------------------------------------------
  // Building, updating and compacting

  /**
   * Builds an index, replacing any there is.
   *
   * @param scan the games, opened with the filter
   * @param database what identifies the database the games are of
   * @param filter the filter the scan was opened with, recorded in the index (see {@link
   *     PositionIndex#isStale}); blank for every game
   * @param indexDir the index directory, see {@link IndexFiles#indexDirectoryOf}
   * @return what the index holds
   */
  public @NotNull IndexMeta build(
      @NotNull GameScan scan,
      @NotNull DatabaseIdentity database,
      @NotNull String filter,
      @NotNull Path indexDir)
      throws IOException {
    Path building = indexDir.resolveSibling(indexDir.getFileName() + ".building");
    IndexFiles.deleteDirectory(building);
    Files.createDirectories(building);
    boolean done = false;
    try {
      GameFactsTable facts = GameFactsTable.forGames(scan.maxId());
      Batches batches = new Batches(building, facts.maxId());
      collect(scan, 1, new int[0], facts, batches);
      int newest = facts.newestYear();
      int recentSince = newest > 0 ? newest - 2 : 0;
      String segment = segmentName(1);
      SegmentMeta written = batches.finish(building.resolve(segment), facts, recentSince);
      String factsFile = factsName(1);
      facts.write(building.resolve(factsFile));
      Instant now = Instant.now();
      IndexMeta meta =
          new IndexMeta(
              IndexMeta.FORMAT_VERSION,
              now,
              now,
              database,
              filter.strip(),
              recentSince,
              facts.count(),
              facts.count(),
              scan.maxId(),
              factsFile,
              List.of(segment),
              written.sharedPositions(),
              written.singlePositions());
      meta.write(building);
      IndexFiles.deleteDirectory(indexDir);
      Files.move(building, indexDir);
      done = true;
      return meta;
    } finally {
      if (!done) {
        IndexFiles.deleteDirectory(building);
      }
    }
  }

  /**
   * Adds the games added to the database since an index was built or last updated, as a segment
   * of its own; compacts the index if it has grown too many segments, or too large newer ones.
   *
   * @param scan the games, opened with the index's filter
   * @param database what identifies the database now
   * @param changed games changed or deleted since, which are indexed again (or no longer, if they
   *     are no more or don't match the filter)
   * @return what the index holds
   */
  public @NotNull IndexMeta update(
      @NotNull GameScan scan,
      @NotNull DatabaseIdentity database,
      @NotNull Path indexDir,
      int @NotNull ... changed)
      throws IOException {
    IndexMeta meta = IndexMeta.read(indexDir);
    GameFactsTable facts =
        GameFactsTable.read(indexDir.resolve(meta.facts())).grownTo(scan.maxId());
    int[] superseded = Arrays.stream(changed).filter(id -> id > 0).sorted().distinct().toArray();
    for (int id : superseded) {
      if (id <= facts.maxId()) {
        facts.clear(id);
      }
    }
    int number = nextNumber(meta);
    String segment = segmentName(number);
    Path segmentDir = indexDir.resolve(segment);
    Path work = indexDir.resolve("batches-" + number);
    IndexFiles.deleteDirectory(segmentDir);
    IndexFiles.deleteDirectory(work);
    Files.createDirectories(work);
    SegmentMeta written;
    try {
      Batches batches = new Batches(work, facts.maxId());
      collect(scan, meta.lastGameId() + 1, superseded, facts, batches);
      written = batches.finish(segmentDir, facts, meta.recentSince());
    } catch (IOException | RuntimeException e) {
      IndexFiles.deleteDirectory(segmentDir);
      throw e;
    } finally {
      IndexFiles.deleteDirectory(work);
    }
    List<String> segments = new ArrayList<>(meta.segments());
    if (superseded.length > 0) {
      IndexFiles.writeInts(segmentDir.resolve(IndexFiles.SUPERSEDES), superseded);
    }
    if (superseded.length > 0 || written.sharedPositions() + written.singlePositions() > 0) {
      segments.add(segment);
    } else {
      // Nothing new: the database changed in no game the index holds
      IndexFiles.deleteDirectory(segmentDir);
    }
    String factsFile = factsName(number);
    facts.write(indexDir.resolve(factsFile));
    IndexMeta updated =
        new IndexMeta(
            IndexMeta.FORMAT_VERSION,
            meta.builtAt(),
            Instant.now(),
            database,
            meta.filter(),
            meta.recentSince(),
            facts.count(),
            meta.baseGames(),
            scan.maxId(),
            factsFile,
            segments,
            meta.sharedPositions() + written.sharedPositions(),
            meta.singlePositions() + written.singlePositions());
    updated.write(indexDir);
    removeUnused(indexDir, updated);
    progress.accept(
        segments.contains(segment)
            ? String.format(
                Locale.ROOT,
                "Added %,d positions in segment %s",
                written.sharedPositions() + written.singlePositions(),
                segment)
            : "No games to add");
    double newerShare = updated.games() == 0 ? 0 : 1 - (double) updated.baseGames() / updated.games();
    if (segments.size() > MAX_SEGMENTS || newerShare > MAX_NEWER_SHARE) {
      return compact(indexDir);
    }
    return updated;
  }

  /**
   * Merges an index's segments into one, leaving out the entries of superseded games; the
   * statistics are worked out again, with the recent games counted from two years before the
   * newest game.
   *
   * @return what the index holds
   */
  public @NotNull IndexMeta compact(@NotNull Path indexDir) throws IOException {
    long start = System.nanoTime();
    IndexMeta updated;
    try (PositionIndex index = PositionIndex.open(indexDir)) {
      IndexMeta meta = index.meta();
      GameFactsTable facts = index.facts();
      int newest = facts.newestYear();
      int recentSince = newest > 0 ? newest - 2 : 0;
      int number = nextNumber(meta);
      String segment = segmentName(number);
      Path segmentDir = indexDir.resolve(segment);
      IndexFiles.deleteDirectory(segmentDir);
      long singles =
          index.segments().stream().mapToLong(s -> s.meta().singlePositions()).sum();
      SegmentMeta written;
      try {
        try (SegmentWriter out =
            new SegmentWriter(segmentDir, facts, recentSince, singles, facts.maxId())) {
          SegmentMerger.merge(index.segments(), index.superseded(), out, progress);
        }
        written = SegmentMeta.read(segmentDir.resolve(IndexFiles.SEGMENT_META));
      } catch (IOException | RuntimeException e) {
        IndexFiles.deleteDirectory(segmentDir);
        throw e;
      }
      updated =
          new IndexMeta(
              IndexMeta.FORMAT_VERSION,
              meta.builtAt(),
              meta.updatedAt(),
              meta.database(),
              meta.filter(),
              recentSince,
              facts.count(),
              facts.count(),
              meta.lastGameId(),
              meta.facts(),
              List.of(segment),
              written.sharedPositions(),
              written.singlePositions());
      updated.write(indexDir);
    }
    removeUnused(indexDir, updated);
    progress.accept(
        String.format(
            Locale.ROOT,
            "Compacted into one segment in %.0f s",
            (System.nanoTime() - start) / 1e9));
    return updated;
  }

  private static String segmentName(int number) {
    return String.format(Locale.ROOT, "seg-%04d", number);
  }

  private static String factsName(int number) {
    return String.format(Locale.ROOT, "facts-%04d.bin", number);
  }

  /** The number after the highest of an index's segments. */
  private static int nextNumber(IndexMeta meta) {
    int highest = 0;
    for (String segment : meta.segments()) {
      highest = Math.max(highest, Integer.parseInt(segment.substring("seg-".length())));
    }
    return highest + 1;
  }

  /** Deletes what's in an index directory that its manifest doesn't name. */
  private static void removeUnused(Path indexDir, IndexMeta meta) throws IOException {
    Set<String> used = new HashSet<>(meta.segments());
    used.add(meta.facts());
    used.add(IndexFiles.MANIFEST);
    try (var files = Files.list(indexDir)) {
      for (Path f : files.toList()) {
        if (!used.contains(f.getFileName().toString())) {
          if (Files.isDirectory(f)) {
            IndexFiles.deleteDirectory(f);
          } else {
            Files.delete(f);
          }
        }
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // The games' positions, collected

  /** The record of a position of a game: the game, the side to move, and the move played. */
  static long payload(int gameId, boolean whiteToMove, int moveCode) {
    return ((long) gameId << 17) | (whiteToMove ? 1L << 16 : 0) | moveCode;
  }

  static int gameOf(long payload) {
    return (int) (payload >>> 17);
  }

  static boolean whiteToMoveOf(long payload) {
    return (payload & (1L << 16)) != 0;
  }

  static int moveOf(long payload) {
    return (int) (payload & 0xFFFF);
  }

  /** What a thread reading games keeps: its records not yet passed on, and a game's positions. */
  private static final class Worker {
    final long[] chunk = new long[2 * CHUNK];
    int size;
    final GamePositions positions = new GamePositions();
  }

  /**
   * Gives the positions of the games from an id on, and of some games by id, to the batches, and
   * their facts to the table; the batches are left with the records not yet written out.
   */
  private void collect(
      GameScan scan, int firstId, int[] games, GameFactsTable facts, Batches batches)
      throws IOException {
    AtomicLong done = new AtomicLong();
    long start = System.nanoTime();
    ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor();
    reporter.scheduleAtFixedRate(
        () ->
            progress.accept(
                String.format(
                    Locale.ROOT,
                    "%,d games read, %.0f s",
                    done.get(),
                    (System.nanoTime() - start) / 1e9)),
        5,
        5,
        TimeUnit.SECONDS);
    Queue<Worker> workers = new ConcurrentLinkedQueue<>();
    ThreadLocal<Worker> worker =
        ThreadLocal.withInitial(
            () -> {
              Worker w = new Worker();
              workers.add(w);
              return w;
            });
    GameScan.MainLineVisitor visitor =
        (id, gameFacts, line) -> {
          try {
            Worker w = worker.get();
            facts.set(id, gameFacts);
            addPositions(id, line, w, batches);
          } catch (UncheckedIOException e) {
            throw e;
          } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to index game " + id + ": " + e, e);
          }
          done.incrementAndGet();
        };
    try {
      if (firstId <= scan.maxId()) {
        scan.forEachMainLine(firstId, visitor);
      }
      for (int id : games) {
        // Games after firstId were just indexed
        ScannedGame game = id < firstId && id <= scan.maxId() ? scan.read(id) : null;
        if (game != null) {
          visitor.visit(id, game.facts(), MainLine.of(game.moves()));
        }
      }
      for (Worker w : workers) {
        batches.add(w.chunk, w.size);
        w.size = 0;
      }
    } catch (UncheckedIOException e) {
      throw e.getCause();
    } finally {
      reporter.shutdownNow();
    }
    progress.accept(
        String.format(
            Locale.ROOT,
            "%,d games read in %.0f s",
            done.get(),
            (System.nanoTime() - start) / 1e9));
  }

  /**
   * Gives the records of a game's positions to a worker's chunk, each position once, played through
   * on the main-line cursor, which a format can do without making positions or moves.
   */
  private static void addPositions(int id, MainLine line, Worker w, Batches batches) {
    w.positions.clear();
    while (true) {
      long hash = line.hash();
      int code = line.moveCode();
      if (w.positions.add(hash)) {
        int move = code == MoveCode.NONE ? IndexFiles.GAME_ENDED : code;
        w.chunk[2 * w.size] = hash;
        w.chunk[2 * w.size + 1] = payload(id, line.whiteToMove(), move);
        if (++w.size == CHUNK) {
          batches.add(w.chunk, w.size);
          w.size = 0;
        }
      }
      if (code == MoveCode.NONE) {
        return;
      }
      line.advance();
    }
  }

  /** The positions of a game seen so far, to count each one once. */
  private static final class GamePositions {
    private final long[] slots = new long[1024];
    private final int[] generations = new int[1024];
    private int generation;

    void clear() {
      generation++;
    }

    /** Adds a position; false if the game has been in it before. */
    boolean add(long hash) {
      int mask = slots.length - 1;
      int i = (int) (hash ^ (hash >>> 29)) & mask;
      for (int probes = 0; probes < slots.length; probes++, i = (i + 1) & mask) {
        if (generations[i] != generation) {
          generations[i] = generation;
          slots[i] = hash;
          return true;
        }
        if (slots[i] == hash) {
          return false;
        }
      }
      // A game of more than a thousand positions; the rest count as new
      return true;
    }
  }

  // ---------------------------------------------------------------------------------------------
  // The records, in batches

  /**
   * The records collected, hash and payload after each other: when they're as many as a batch
   * holds, they're sorted and written out as a segment of their own, without statistics, to be
   * merged.
   */
  private final class Batches {
    private final Path dir;
    private final int maxGameId;
    private final List<Path> written = new ArrayList<>();
    private long[] records = new long[2 * CHUNK];
    private int size;

    /**
     * @param dir where the batches' segments go
     * @param maxGameId the highest game id there can be
     */
    Batches(Path dir, int maxGameId) {
      this.dir = dir;
      this.maxGameId = maxGameId;
    }

    synchronized void add(long[] chunk, int n) {
      if (size + n > batchRecords && size > 0) {
        try {
          Path segment = dir.resolve(String.format(Locale.ROOT, "batch-%04d", written.size() + 1));
          write(segment, null, 0);
          written.add(segment);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
      if (2 * (size + n) > records.length) {
        // Doubled, up to a batch
        int most = (int) Math.min(Integer.MAX_VALUE - 8, 2 * (batchRecords + CHUNK));
        records = Arrays.copyOf(records, Math.max(2 * (size + n), Math.min(2 * records.length, most)));
      }
      System.arraycopy(chunk, 0, records, 2 * size, 2 * n);
      size += n;
    }

    /** Sorts the records collected and writes them out as a segment; forgets them. */
    private SegmentMeta write(Path segment, GameFactsTable facts, int recentSince)
        throws IOException {
      long start = System.nanoTime();
      long[] scratch = new long[2 * size];
      sortByHash(records, size, scratch);
      scratch = null;
      try (SegmentWriter out = new SegmentWriter(segment, facts, recentSince, size, maxGameId)) {
        writePositions(records, size, out);
      }
      progress.accept(
          String.format(
              Locale.ROOT,
              "%,d positions sorted and written in %.0f s",
              size,
              (System.nanoTime() - start) / 1e9));
      size = 0;
      return SegmentMeta.read(segment.resolve(IndexFiles.SEGMENT_META));
    }

    /**
     * Writes the segment of every record: the records left, written out directly if they're all
     * there are, or else with the segments written before merged into it.
     */
    synchronized SegmentMeta finish(Path segment, GameFactsTable facts, int recentSince)
        throws IOException {
      if (written.isEmpty()) {
        return write(segment, facts, recentSince);
      }
      if (size > 0) {
        Path last = dir.resolve(String.format(Locale.ROOT, "batch-%04d", written.size() + 1));
        write(last, null, 0);
        written.add(last);
      }
      records = null;
      List<SegmentReader> readers = new ArrayList<>();
      try {
        long singles = 0;
        for (Path batch : written) {
          SegmentReader reader = SegmentReader.open(batch);
          readers.add(reader);
          singles += reader.meta().singlePositions();
        }
        try (SegmentWriter out =
            new SegmentWriter(segment, facts, recentSince, singles, maxGameId)) {
          SegmentMerger.merge(readers, new SupersededGames(List.of(), 0), out, progress);
        }
      } finally {
        for (SegmentReader reader : readers) {
          reader.close();
        }
        for (Path batch : written) {
          IndexFiles.deleteDirectory(batch);
        }
      }
      return SegmentMeta.read(segment.resolve(IndexFiles.SEGMENT_META));
    }
  }

  /**
   * Sorts the first {@code n} hash-payload pairs of an array by unsigned hash: spread by the top 8
   * bits into a scratch array, then each part sorted by the other bits (an LSD radix sort, 8 bits a
   * pass) on its own thread, back into the array.
   */
  static void sortByHash(long[] records, int n, long[] scratch) {
    int[] starts = new int[257];
    for (int i = 0; i < n; i++) {
      starts[(int) (records[2 * i] >>> 56) + 1]++;
    }
    for (int b = 0; b < 256; b++) {
      starts[b + 1] += starts[b];
    }
    int[] next = Arrays.copyOf(starts, 256);
    for (int i = 0; i < n; i++) {
      int t = next[(int) (records[2 * i] >>> 56)]++;
      scratch[2 * t] = records[2 * i];
      scratch[2 * t + 1] = records[2 * i + 1];
    }
    // Seven passes, from the scratch array to the array and back, end in the array
    IntStream.range(0, 256)
        .parallel()
        .forEach(b -> sortRange(scratch, records, starts[b], starts[b + 1]));
  }

  /** Sorts a range of pairs by the low 56 bits of the hash, from one array into the other. */
  private static void sortRange(long[] from, long[] to, int start, int end) {
    if (end - start < 2) {
      if (end > start) {
        to[2 * start] = from[2 * start];
        to[2 * start + 1] = from[2 * start + 1];
      }
      return;
    }
    int[] counts = new int[257];
    for (int shift = 0; shift < 56; shift += 8) {
      Arrays.fill(counts, 0);
      for (int i = start; i < end; i++) {
        counts[(int) ((from[2 * i] >>> shift) & 0xFF) + 1]++;
      }
      counts[0] = start;
      for (int i = 0; i < 256; i++) {
        counts[i + 1] += counts[i];
      }
      for (int i = start; i < end; i++) {
        int t = counts[(int) ((from[2 * i] >>> shift) & 0xFF)]++;
        to[2 * t] = from[2 * i];
        to[2 * t + 1] = from[2 * i + 1];
      }
      long[] swap = from;
      from = to;
      to = swap;
    }
  }

  /** Writes sorted records as positions: the games of each by the move they played. */
  private static void writePositions(long[] records, int n, SegmentWriter out) throws IOException {
    int i = 0;
    while (i < n) {
      long hash = records[2 * i];
      int j = i + 1;
      while (j < n && records[2 * j] == hash) {
        j++;
      }
      boolean whiteToMove = whiteToMoveOf(records[2 * i + 1]);
      long[] byMove = new long[j - i];
      for (int k = i; k < j; k++) {
        long payload = records[2 * k + 1];
        byMove[k - i] = ((long) moveOf(payload) << 40) | gameOf(payload);
      }
      Arrays.sort(byMove);
      List<MoveGroup> groups = new ArrayList<>();
      int k = 0;
      while (k < byMove.length) {
        int move = (int) (byMove[k] >>> 40);
        int end = k + 1;
        while (end < byMove.length && byMove[end] >>> 40 == move) {
          end++;
        }
        int[] ids = new int[end - k];
        for (int g = k; g < end; g++) {
          ids[g - k] = (int) (byMove[g] & ((1L << 40) - 1));
        }
        groups.add(new MoveGroup(move, ids, null));
        k = end;
      }
      out.add(new PositionEntry(hash, whiteToMove, groups));
      i = j;
    }
  }
}
