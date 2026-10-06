package se.yarin.morphy.positions;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Player;
import se.yarin.chess.Position;
import se.yarin.morphy.api.GameScan;
import se.yarin.morphy.api.ScannedGame;

/**
 * Builds the position index of a database from a {@link GameScan}.
 *
 * <p>The games are decoded in parallel, and each position of a game's main line becomes a record
 * of its hash, the game, the side to move and the move played from it, written to one of 256
 * bucket files by the top bits of the hash. A position repeated in a game counts once, with the
 * move first played from it. Each bucket is then sorted by hash and its positions written out in
 * hash order: those several games reached to the shared files, with their moves, games and, for
 * moves played in {@link #STATS_THRESHOLD} games or more, statistics; and those one game reached
 * to the single-game files, as a 16-bit check and the game id, by a directory of the top 24 bits
 * of the hash.
 *
 * <p>The index is built in a directory next to the final one and moved in place when complete.
 * The buckets need 16 bytes per position and game in the work directory (some 15 GB for a
 * Megabase), and are deleted as they're read.
 */
public final class PositionIndexBuilder {
  /** The fewest games of a move whose statistics are stored. */
  public static final int STATS_THRESHOLD = 50;

  private static final int BUCKET_BITS = 8;
  private static final int BUCKETS = 1 << BUCKET_BITS;
  // Records per bucket a worker collects before writing them out
  private static final int BUFFERED = 2048;

  private final @NotNull Consumer<String> progress;

  /** @param progress told how the build goes, every few seconds */
  public PositionIndexBuilder(@NotNull Consumer<String> progress) {
    this.progress = progress;
  }

  /**
   * Builds an index, replacing any there is.
   *
   * @param scan the games
   * @param database what identifies the database the games are of
   * @param indexDir the index directory, see {@link IndexFiles#indexDirectoryOf}
   * @param workDir where the buckets are written
   * @return what the index holds
   */
  public @NotNull IndexMeta build(
      @NotNull GameScan scan,
      @NotNull DatabaseIdentity database,
      @NotNull Path indexDir,
      @NotNull Path workDir)
      throws IOException {
    Path building = indexDir.resolveSibling(indexDir.getFileName() + ".building");
    IndexFiles.deleteDirectory(building);
    Files.createDirectories(building);
    Files.createDirectories(workDir);
    Path buckets = Files.createTempDirectory(workDir, "positions-");
    boolean done = false;
    try {
      GameFactsTable facts = GameFactsTable.forGames(scan.maxId());
      decode(scan, facts, buckets);
      int newest = facts.newestYear();
      int recentSince = newest > 0 ? newest - 2 : 0;
      int gameIdBytes = scan.maxId() < (1 << 24) ? 3 : 4;
      long[] counts = write(buckets, facts, recentSince, gameIdBytes, building);
      facts.write(building.resolve(IndexFiles.FACTS));
      IndexMeta meta =
          new IndexMeta(
              IndexMeta.FORMAT_VERSION,
              Instant.now(),
              database,
              recentSince,
              STATS_THRESHOLD,
              gameIdBytes,
              counts[0],
              counts[1]);
      meta.write(building.resolve(IndexFiles.META));
      IndexFiles.deleteDirectory(indexDir);
      Files.move(building, indexDir);
      done = true;
      return meta;
    } finally {
      IndexFiles.deleteDirectory(buckets);
      if (!done) {
        IndexFiles.deleteDirectory(building);
      }
    }
  }

  private static Path bucketFile(Path dir, int bucket) {
    return dir.resolve(String.format("bucket-%03d.bin", bucket));
  }

  // ---------------------------------------------------------------------------------------------
  // The games' positions into the buckets

  /** The bucket files, appended to by the workers a buffer at a time. */
  private static final class Buckets implements AutoCloseable {
    private final DataOutputStream[] out = new DataOutputStream[BUCKETS];

    Buckets(Path dir) throws IOException {
      for (int b = 0; b < BUCKETS; b++) {
        out[b] =
            new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(bucketFile(dir, b)), 1 << 16));
      }
    }

    void write(int bucket, long[] records, int n) {
      DataOutputStream o = out[bucket];
      synchronized (o) {
        try {
          for (int i = 0; i < 2 * n; i++) {
            o.writeLong(records[i]);
          }
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }

    @Override
    public void close() throws IOException {
      for (DataOutputStream o : out) {
        o.close();
      }
    }
  }

  /** A worker's records not yet written, per bucket: hash and payload after each other. */
  private static final class Buffer {
    final long[][] records = new long[BUCKETS][2 * BUFFERED];
    final int[] sizes = new int[BUCKETS];
    final Buckets buckets;

    Buffer(Buckets buckets) {
      this.buckets = buckets;
    }

    void add(long hash, long payload) {
      int b = (int) (hash >>> (64 - BUCKET_BITS));
      int n = sizes[b];
      records[b][2 * n] = hash;
      records[b][2 * n + 1] = payload;
      if (++sizes[b] == BUFFERED) {
        buckets.write(b, records[b], BUFFERED);
        sizes[b] = 0;
      }
    }

    void flush() {
      for (int b = 0; b < BUCKETS; b++) {
        buckets.write(b, records[b], sizes[b]);
        sizes[b] = 0;
      }
    }
  }

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

  /** What a thread reading games keeps: its records not yet written, and a game's positions. */
  private record Worker(Buffer buffer, GamePositions positions) {}

  private void decode(GameScan scan, GameFactsTable facts, Path dir) throws IOException {
    int maxId = scan.maxId();
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
    try (Buckets buckets = new Buckets(dir)) {
      // A worker per thread the scan reads games on, flushed when all are read
      Queue<Worker> workers = new ConcurrentLinkedQueue<>();
      ThreadLocal<Worker> worker =
          ThreadLocal.withInitial(
              () -> {
                Worker w = new Worker(new Buffer(buckets), new GamePositions());
                workers.add(w);
                return w;
              });
      scan.forEach(
          game -> {
            try {
              Worker w = worker.get();
              facts.set(game.id(), game.facts());
              addPositions(game, w.positions(), w.buffer());
            } catch (RuntimeException e) {
              throw new IllegalStateException(
                  "Failed to index game " + game.id() + ": " + e, e);
            }
            done.incrementAndGet();
          });
      for (Worker w : workers) {
        w.buffer().flush();
      }
    } catch (UncheckedIOException e) {
      throw e.getCause();
    } finally {
      reporter.shutdownNow();
    }
    progress.accept(
        String.format(
            Locale.ROOT,
            "%,d games of %,d records read in %.0f s",
            done.get(),
            maxId,
            (System.nanoTime() - start) / 1e9));
  }

  private static void addPositions(ScannedGame game, GamePositions positions, Buffer buffer) {
    positions.clear();
    GameMovesModel.Node node = game.moves().root();
    while (true) {
      Position position = node.position();
      long hash = PositionKeys.hash(position);
      GameMovesModel.Node next = node.hasMoves() ? node.mainNode() : null;
      if (positions.add(hash)) {
        int move = next == null ? PositionKeys.GAME_ENDED : PositionKeys.moveCode(next.lastMove());
        buffer.add(hash, payload(game.id(), position.playerToMove() == Player.WHITE, move));
      }
      if (next == null) {
        return;
      }
      node = next;
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
  // The buckets sorted and written out

  /** Sorts hash-payload pairs by hash: an LSD radix sort, 8 bits a pass. */
  static void sortByHash(long[] records) {
    int n = records.length / 2;
    long[] other = new long[records.length];
    long[] from = records, to = other;
    int[] counts = new int[257];
    // The top bits are the bucket's, the same for all
    for (int shift = 0; shift < 64 - BUCKET_BITS; shift += 8) {
      Arrays.fill(counts, 0);
      for (int i = 0; i < n; i++) {
        counts[(int) ((from[2 * i] >>> shift) & 0xFF) + 1]++;
      }
      for (int i = 0; i < 256; i++) {
        counts[i + 1] += counts[i];
      }
      for (int i = 0; i < n; i++) {
        int t = counts[(int) ((from[2 * i] >>> shift) & 0xFF)]++;
        to[2 * t] = from[2 * i];
        to[2 * t + 1] = from[2 * i + 1];
      }
      long[] swap = from;
      from = to;
      to = swap;
    }
    if (from != records) {
      System.arraycopy(from, 0, records, 0, records.length);
    }
  }

  /**
   * A bucket's positions, ready to be written: those several games reached, as their keys and
   * records (with offsets from the bucket's first), and those one game reached, as their entries.
   */
  private record PreparedBucket(long[] keys, long[] offsets, Bytes sharedData, Bytes singleData) {}

  // Buckets sorted at once: each takes some 30 bytes per record while sorted, up to 500 MB for the
  // largest bucket of a Megabase
  private static final int SORTED_AT_ONCE = Math.min(4, Runtime.getRuntime().availableProcessors());

  /**
   * Writes the index files, the buckets sorted several at a time and written in order; returns
   * the numbers of shared and single-game positions.
   */
  private long[] write(
      Path buckets, GameFactsTable facts, int recentSince, int gameIdBytes, Path dir)
      throws IOException {
    long start = System.nanoTime();
    long shared = 0, single = 0, sharedOffset = 0;
    // Each bucket counts its own part of the directory, by the top bits of the hash
    int[] directory = new int[(1 << IndexFiles.DIRECTORY_BITS) + 1];
    ExecutorService pool = Executors.newFixedThreadPool(SORTED_AT_ONCE);
    List<Future<PreparedBucket>> prepared = new ArrayList<>();
    try (DataOutputStream keys = output(dir.resolve(IndexFiles.SHARED_KEYS));
        DataOutputStream offsets = output(dir.resolve(IndexFiles.SHARED_OFFSETS));
        DataOutputStream sharedData = output(dir.resolve(IndexFiles.SHARED_DATA));
        DataOutputStream singleData = output(dir.resolve(IndexFiles.SINGLE_DATA))) {
      for (int b = 0; b < BUCKETS; b++) {
        while (prepared.size() < Math.min(BUCKETS, b + SORTED_AT_ONCE)) {
          Path file = bucketFile(buckets, prepared.size());
          prepared.add(
              pool.submit(() -> prepare(file, facts, recentSince, gameIdBytes, directory)));
        }
        PreparedBucket bucket = prepared.get(b).get();
        // Let the bucket be collected once written
        prepared.set(b, null);
        for (int k = 0; k < bucket.keys().length; k++) {
          keys.writeLong(bucket.keys()[k]);
          offsets.writeLong(sharedOffset + bucket.offsets()[k]);
        }
        sharedData.write(bucket.sharedData().array(), 0, bucket.sharedData().size());
        singleData.write(bucket.singleData().array(), 0, bucket.singleData().size());
        sharedOffset += bucket.sharedData().size();
        shared += bucket.keys().length;
        single += bucket.singleData().size() / (2 + gameIdBytes);
        if (b % 32 == 31) {
          progress.accept(
              String.format(
                  Locale.ROOT,
                  "%d of %d buckets written, %.0f s",
                  b + 1,
                  BUCKETS,
                  (System.nanoTime() - start) / 1e9));
        }
      }
      offsets.writeLong(sharedOffset);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof IOException io) {
        throw io;
      }
      throw new IOException(e.getCause().getMessage(), e.getCause());
    } finally {
      pool.shutdownNow();
    }
    for (int k = 0; k < directory.length - 1; k++) {
      directory[k + 1] += directory[k];
    }
    try (DataOutputStream out = output(dir.resolve(IndexFiles.SINGLE_DIRECTORY))) {
      for (int first : directory) {
        out.writeInt(first);
      }
    }
    return new long[] {shared, single};
  }

  /**
   * Reads a bucket, deletes its file, sorts it and turns its positions into what's written; counts
   * its single-game positions in its part of the directory.
   */
  private static PreparedBucket prepare(
      Path file, GameFactsTable facts, int recentSince, int gameIdBytes, int[] directory)
      throws IOException {
    long[] records = IndexFiles.readLongs(file);
    Files.delete(file);
    sortByHash(records);
    int n = records.length / 2;
    int positions = 0;
    for (int i = 0; i < n; i++) {
      if (i == 0 || records[2 * i] != records[2 * i - 2]) {
        positions++;
      }
    }
    long[] keys = new long[positions];
    long[] offsets = new long[positions];
    int shared = 0;
    Bytes sharedData = new Bytes();
    Bytes singleData = new Bytes();
    int i = 0;
    while (i < n) {
      long hash = records[2 * i];
      int j = i + 1;
      while (j < n && records[2 * j] == hash) {
        j++;
      }
      if (j - i == 1) {
        int game = gameOf(records[2 * i + 1]);
        singleData.putShort((int) (hash >>> 24));
        if (gameIdBytes == 4) {
          singleData.put(game >>> 24);
        }
        singleData.putShort(game >>> 8);
        singleData.put(game);
        directory[(int) (hash >>> (64 - IndexFiles.DIRECTORY_BITS)) + 1]++;
      } else {
        keys[shared] = hash;
        offsets[shared] = sharedData.size();
        shared++;
        writePosition(records, i, j, facts, recentSince, sharedData);
      }
      i = j;
    }
    return new PreparedBucket(
        Arrays.copyOf(keys, shared), Arrays.copyOf(offsets, shared), sharedData, singleData);
  }

  private static DataOutputStream output(Path file) throws IOException {
    return new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file), 1 << 20));
  }

  /**
   * A position several games reached: the number of moves, then per move its code, its number of
   * games, a flag byte (1 if the statistics follow), the statistics, and the game ids, each as the
   * difference from the one before.
   */
  private static void writePosition(
      long[] records, int from, int to, GameFactsTable facts, int recentSince, Bytes out) {
    boolean whiteToMove = whiteToMoveOf(records[2 * from + 1]);
    long[] byMove = new long[to - from];
    for (int k = from; k < to; k++) {
      long payload = records[2 * k + 1];
      byMove[k - from] = ((long) moveOf(payload) << 40) | gameOf(payload);
    }
    Arrays.sort(byMove);
    int groups = 0;
    for (int k = 0; k < byMove.length; k++) {
      if (k == 0 || byMove[k] >>> 40 != byMove[k - 1] >>> 40) {
        groups++;
      }
    }
    out.putVar(groups);
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
      out.putShort(move);
      out.putVar(ids.length);
      boolean stats = ids.length >= STATS_THRESHOLD && move != PositionKeys.GAME_ENDED;
      out.put(stats ? 1 : 0);
      if (stats) {
        MoveStats.of(ids, facts, whiteToMove, recentSince).write(out);
      }
      int previous = 0;
      for (int id : ids) {
        out.putVar(id - previous);
        previous = id;
      }
      k = end;
    }
  }
}
