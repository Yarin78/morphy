package se.yarin.morphy.tools;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import se.yarin.chess.GameMovesModel;
import se.yarin.chess.Move;
import se.yarin.chess.Position;
import se.yarin.morphy.api.AccessMode;
import se.yarin.morphy.cb2.Database2Cbh;
import se.yarin.morphy.cb2.ReadTransaction;
import se.yarin.morphy.cb2.games.GameHeader;
import se.yarin.morphy.cb2.games.GameRecord;
import se.yarin.morphy.cb2.games.TextHeader;
import se.yarin.morphy.cb2.moves.MoveStreamCodec;
import se.yarin.morphy.cb2.storage.RecordFile;

/**
 * Measures what an index from positions to the games reaching them would hold for a v2 database:
 * how many distinct positions its games' main lines go through, how many of them only one game
 * reaches, how many (position, next move) groups there are of each size, and roughly how much
 * the parts of such an index would take compactly stored.
 *
 * <p>A position is its 64-bit Zobrist hash. A position repeated in a game counts once, with the move first played from
 * it.
 *
 * <p>The first pass decodes the games in parallel and writes a record per position and game into
 * bucket files, split by the top bits of the hash. The second sorts each bucket and counts. The
 * third goes through the sorted buckets again, now that it's known where each game leaves the
 * positions other games reach, to count what an index of only the shared positions would miss.
 *
 * <p>The buckets take 16 bytes per position and game (some 15 GB for a Megabase) and are deleted at
 * the end.
 *
 * <p>Usage: PositionIndexStats &lt;database.2cbh&gt; &lt;work directory&gt; &lt;report file&gt;
 * [max games]
 */
public class PositionIndexStats {
  private static final int BUCKET_BITS = 8;
  private static final int BUCKETS = 1 << BUCKET_BITS;
  // Records per bucket a worker collects before writing them out
  private static final int BUFFERED = 2048;

  // The move from the last position of a game, which has none
  private static final int NO_MOVE = 0xFFFF;
  private static final int NULL_MOVE = 0x7FFF;

  // The sizes of positions and (position, move) groups counted
  private static final int[] THRESHOLDS = {2, 5, 10, 20, 50, 100, 1000, 10_000, 100_000};

  public static void main(String[] args) throws Exception {
    if (args.length < 3) {
      System.err.println(
          "Usage: PositionIndexStats <database.2cbh> <work directory> <report file> [max games]");
      System.exit(1);
    }
    File database = new File(args[0]);
    Path work = Files.createDirectories(Path.of(args[1]));
    int maxGames = args.length > 3 ? Integer.parseInt(args[3]) : Integer.MAX_VALUE;
    Stats stats = new Stats();
    System.err.println("Opening " + database);
    try (Database2Cbh db = Database2Cbh.open(database, AccessMode.READ_ONLY);
        ReadTransaction txn = new ReadTransaction(db)) {
      int count = Math.min(txn.count(), maxGames);
      stats.database = database.getName();
      stats.records = count;
      stats.gameIdUniverse = count;
      decode(db, count, work, stats);
    }
    try {
      int[] branchOff = new int[stats.gameIdUniverse + 1];
      Arrays.fill(branchOff, Integer.MAX_VALUE);
      System.err.println("Sorting and counting the buckets");
      count(work, stats, branchOff);
      System.err.println("Looking for shared positions reached after games leave them");
      missedBeyondBranchOff(work, stats, branchOff);
      stats.branchOff(branchOff);
    } finally {
      for (int b = 0; b < BUCKETS; b++) {
        Files.deleteIfExists(bucketFile(work, b));
      }
    }
    try (PrintWriter out = new PrintWriter(args[2])) {
      stats.report(out);
    }
    try (PrintWriter out = new PrintWriter(System.out)) {
      stats.report(out);
    }
  }

  private static Path bucketFile(Path work, int bucket) {
    return work.resolve(String.format("bucket-%03d.bin", bucket));
  }

  // ---------------------------------------------------------------------------------------------
  // Pass 1: the games' positions into the buckets

  /** The bucket files, appended to by the workers a buffer at a time. */
  private static final class Buckets implements AutoCloseable {
    private final DataOutputStream[] out = new DataOutputStream[BUCKETS];

    Buckets(Path work) throws IOException {
      for (int b = 0; b < BUCKETS; b++) {
        out[b] =
            new DataOutputStream(
                new BufferedOutputStream(
                    new FileOutputStream(bucketFile(work, b).toFile()), 1 << 16));
      }
    }

    void write(int bucket, long[] hashes, long[] payloads, int n) {
      DataOutputStream o = out[bucket];
      synchronized (o) {
        try {
          for (int i = 0; i < n; i++) {
            o.writeLong(hashes[i]);
            o.writeLong(payloads[i]);
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

  /** A worker's records not yet written, per bucket. */
  private static final class Buffer {
    final long[][] hashes = new long[BUCKETS][BUFFERED];
    final long[][] payloads = new long[BUCKETS][BUFFERED];
    final int[] sizes = new int[BUCKETS];
    final Buckets buckets;

    Buffer(Buckets buckets) {
      this.buckets = buckets;
    }

    void add(long hash, long payload) {
      int b = (int) (hash >>> (64 - BUCKET_BITS));
      int n = sizes[b];
      hashes[b][n] = hash;
      payloads[b][n] = payload;
      if (++sizes[b] == BUFFERED) {
        buckets.write(b, hashes[b], payloads[b], BUFFERED);
        sizes[b] = 0;
      }
    }

    void flush() {
      for (int b = 0; b < BUCKETS; b++) {
        buckets.write(b, hashes[b], payloads[b], sizes[b]);
        sizes[b] = 0;
      }
    }
  }

  private static void decode(Database2Cbh db, int count, Path work, Stats stats)
      throws Exception {
    int threads = Runtime.getRuntime().availableProcessors();
    AtomicInteger next = new AtomicInteger(1);
    AtomicLong done = new AtomicLong();
    long start = System.nanoTime();
    System.err.printf(Locale.ROOT, "Decoding %,d records with %d threads%n", count, threads);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    ScheduledExecutorService progress = Executors.newSingleThreadScheduledExecutor();
    progress.scheduleAtFixedRate(
        () -> {
          long d = done.get();
          double s = (System.nanoTime() - start) / 1e9;
          System.err.printf(
              Locale.ROOT, "%,d of %,d decoded, %.0f s, %,.0f records/s%n", d, count, s, d / s);
        },
        5,
        5,
        TimeUnit.SECONDS);
    try (Buckets buckets = new Buckets(work)) {
      List<Future<Stats>> results = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        results.add(
            pool.submit(
                () -> {
                  Stats local = new Stats();
                  Buffer buffer = new Buffer(buckets);
                  GamePositions positions = new GamePositions();
                  int id;
                  while ((id = next.getAndAdd(256)) <= count) {
                    for (int i = id; i < Math.min(id + 256, count + 1); i++) {
                      try {
                        decodeGame(db, i, positions, buffer, local);
                      } catch (RuntimeException e) {
                        throw new IllegalStateException("Failed at record " + i + ": " + e, e);
                      }
                      done.incrementAndGet();
                    }
                  }
                  buffer.flush();
                  return local;
                }));
      }
      for (Future<Stats> f : results) {
        stats.addDecodeCounts(f.get());
      }
    } finally {
      // Stops the other workers on a failure, so the program ends
      pool.shutdownNow();
      progress.shutdownNow();
    }
    stats.decodeSeconds = (System.nanoTime() - start) / 1e9;
    System.err.printf(Locale.ROOT, "Decoded in %.0f s%n", stats.decodeSeconds);
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

  private static void decodeGame(
      Database2Cbh db, int id, GamePositions positions, Buffer buffer, Stats stats) {
    GameRecord record = db.gameHeaderFile().get(id);
    if (record instanceof TextHeader) {
      stats.texts++;
      return;
    }
    if (!(record instanceof GameHeader header)) {
      stats.analyses++;
      return;
    }
    if (header.deleted()) {
      stats.deleted++;
      return;
    }
    if (header.chess960()) {
      stats.chess960++;
      return;
    }
    GameMovesModel moves;
    try {
      RecordFile.Record data = db.moveFile().read(header.movesOffset());
      // A guiding text's body is in the same file; never decoded as moves
      if (data.tag() != RecordFile.TAG_GAME) {
        stats.notMoves++;
        return;
      }
      moves = MoveStreamCodec.decode(data.tag(), data.content());
    } catch (RuntimeException e) {
      stats.undecodable++;
      return;
    }
    stats.games++;
    if (moves.isSetupPosition()) {
      stats.setupGames++;
    }
    positions.clear();
    GameMovesModel.Node node = moves.root();
    int ply = 0;
    while (true) {
      Position position = node.position();
      long hash = position.getZobristHashLo();
      GameMovesModel.Node nextNode = node.hasMoves() ? node.mainNode() : null;
      stats.occurrences++;
      if (positions.add(hash)) {
        int move = nextNode == null ? NO_MOVE : moveKey(nextNode.lastMove());
        buffer.add(hash, ((long) id << 32) | ((long) Math.min(ply, 0xFFFF) << 16) | move);
      } else {
        stats.repeated++;
      }
      if (nextNode == null) {
        break;
      }
      node = nextNode;
      ply++;
    }
  }

  private static int moveKey(Move move) {
    if (move.isNullMove()) {
      return NULL_MOVE;
    }
    int promotion =
        switch (move.promotionStone().toPiece()) {
          case KNIGHT -> 1;
          case BISHOP -> 2;
          case ROOK -> 3;
          case QUEEN -> 4;
          default -> 0;
        };
    return move.fromSqi() | (move.toSqi() << 6) | (promotion << 12);
  }

  // ---------------------------------------------------------------------------------------------
  // Pass 2: each bucket sorted and counted, and written back sorted

  private static long[][] readBucket(Path file) throws IOException {
    int n = (int) (Files.size(file) / 16);
    long[] hashes = new long[n];
    long[] payloads = new long[n];
    try (DataInputStream in =
        new DataInputStream(new BufferedInputStream(new FileInputStream(file.toFile()), 1 << 20))) {
      for (int i = 0; i < n; i++) {
        hashes[i] = in.readLong();
        payloads[i] = in.readLong();
      }
    }
    return new long[][] {hashes, payloads};
  }

  private static void writeBucket(Path file, long[] hashes, long[] payloads) throws IOException {
    try (DataOutputStream out =
        new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file.toFile()), 1 << 20))) {
      for (int i = 0; i < hashes.length; i++) {
        out.writeLong(hashes[i]);
        out.writeLong(payloads[i]);
      }
    }
  }

  /** Sorts the records by hash, carrying the payloads along: an LSD radix sort, 8 bits a pass. */
  private static void sortByHash(long[] hashes, long[] payloads) {
    int n = hashes.length;
    long[] h2 = new long[n];
    long[] p2 = new long[n];
    int[] counts = new int[257];
    // The top bits are the bucket's, the same for all
    for (int shift = 0; shift < 64 - BUCKET_BITS; shift += 8) {
      Arrays.fill(counts, 0);
      for (long h : hashes) {
        counts[(int) ((h >>> shift) & 0xFF) + 1]++;
      }
      for (int i = 0; i < 256; i++) {
        counts[i + 1] += counts[i];
      }
      for (int i = 0; i < n; i++) {
        int d = (int) ((hashes[i] >>> shift) & 0xFF);
        int to = counts[d]++;
        h2[to] = hashes[i];
        p2[to] = payloads[i];
      }
      System.arraycopy(h2, 0, hashes, 0, n);
      System.arraycopy(p2, 0, payloads, 0, n);
    }
  }

  private static void count(Path work, Stats stats, int[] branchOff) throws IOException {
    long start = System.nanoTime();
    int[] moves = new int[0];
    for (int b = 0; b < BUCKETS; b++) {
      Path file = bucketFile(work, b);
      long[][] bucket = readBucket(file);
      long[] hashes = bucket[0];
      long[] payloads = bucket[1];
      sortByHash(hashes, payloads);
      stats.largestBucket = Math.max(stats.largestBucket, hashes.length);
      int i = 0;
      while (i < hashes.length) {
        int j = i;
        while (j < hashes.length && hashes[j] == hashes[i]) {
          j++;
        }
        int games = j - i;
        if (moves.length < games) {
          moves = new int[Math.max(games, 2 * moves.length)];
        }
        for (int k = i; k < j; k++) {
          moves[k - i] = (int) (payloads[k] & 0xFFFF);
        }
        stats.position(games, moves);
        if (games == 1) {
          int game = (int) (payloads[i] >>> 32);
          int ply = (int) ((payloads[i] >>> 16) & 0xFFFF);
          branchOff[game] = Math.min(branchOff[game], ply);
        }
        i = j;
      }
      writeBucket(file, hashes, payloads);
      if (b % 32 == 31) {
        System.err.printf(
            Locale.ROOT,
            "%d of %d buckets counted, %.0f s%n",
            b + 1,
            BUCKETS,
            (System.nanoTime() - start) / 1e9);
      }
    }
    stats.countSeconds = (System.nanoTime() - start) / 1e9;
  }

  // ---------------------------------------------------------------------------------------------
  // Pass 3: the shared positions that games only reach after leaving the ones other games reach

  private static void missedBeyondBranchOff(Path work, Stats stats, int[] branchOff)
      throws IOException {
    for (int b = 0; b < BUCKETS; b++) {
      long[][] bucket = readBucket(bucketFile(work, b));
      long[] hashes = bucket[0];
      long[] payloads = bucket[1];
      int i = 0;
      while (i < hashes.length) {
        int j = i;
        while (j < hashes.length && hashes[j] == hashes[i]) {
          j++;
        }
        if (j - i >= 2) {
          // Reached by a game only after it left the shared positions, if by any
          int beyond = 0;
          for (int k = i; k < j; k++) {
            int game = (int) (payloads[k] >>> 32);
            int ply = (int) ((payloads[k] >>> 16) & 0xFFFF);
            if (ply > branchOff[game]) {
              beyond++;
            }
          }
          stats.sharedOccurrencesBeyond += beyond;
          if (beyond == j - i) {
            stats.sharedPositionsOnlyBeyond++;
          }
          if (beyond > 0) {
            stats.sharedPositionsSomeBeyond++;
          }
        }
        i = j;
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // The counts

  private static final class Stats {
    String database;
    int records;
    int gameIdUniverse;

    // Pass 1
    long games, texts, analyses, deleted, notMoves, chess960, undecodable, setupGames;
    long occurrences, repeated;
    double decodeSeconds;

    // Pass 2
    long entries, distinct, singletons;
    long[] positionsAtLeast = new long[THRESHOLDS.length];
    long[] entriesAtLeast = new long[THRESHOLDS.length];
    long moveGroups, moveGroupsOfShared, lastPositions;
    long[] moveGroupsAtLeast = new long[THRESHOLDS.length];
    long largestPosition;
    int largestBucket;
    // Elias-Fano bits of the game id lists, per shared position and per move group of them
    double positionListBits, moveListBits;
    double countSeconds;

    // Pass 3
    long sharedOccurrencesBeyond, sharedPositionsOnlyBeyond, sharedPositionsSomeBeyond;

    // The plies at which games leave the shared positions
    long gamesNeverBranching;
    int[] branchOffPercentiles;
    final int[] percentiles = {10, 25, 50, 75, 90, 99};

    void addDecodeCounts(Stats o) {
      games += o.games;
      texts += o.texts;
      analyses += o.analyses;
      deleted += o.deleted;
      notMoves += o.notMoves;
      chess960 += o.chess960;
      undecodable += o.undecodable;
      setupGames += o.setupGames;
      occurrences += o.occurrences;
      repeated += o.repeated;
    }

    /** A position, the games reaching it and the move each played from it (n of them). */
    void position(int n, int[] moves) {
      entries += n;
      distinct++;
      largestPosition = Math.max(largestPosition, n);
      if (n == 1) {
        singletons++;
      }
      for (int t = 0; t < THRESHOLDS.length; t++) {
        if (n >= THRESHOLDS[t]) {
          positionsAtLeast[t]++;
          entriesAtLeast[t] += n;
        }
      }
      if (n >= 2) {
        positionListBits += eliasFanoBits(n, gameIdUniverse);
      }
      Arrays.sort(moves, 0, n);
      int i = 0;
      while (i < n) {
        int j = i;
        while (j < n && moves[j] == moves[i]) {
          j++;
        }
        int m = j - i;
        if (moves[i] == NO_MOVE) {
          lastPositions += m;
        } else {
          moveGroups++;
          if (n >= 2) {
            moveGroupsOfShared++;
            moveListBits += eliasFanoBits(m, gameIdUniverse);
          }
          for (int t = 0; t < THRESHOLDS.length; t++) {
            if (m >= THRESHOLDS[t]) {
              moveGroupsAtLeast[t]++;
            }
          }
        }
        i = j;
      }
    }

    void branchOff(int[] branchOff) {
      int[] plies = new int[branchOff.length];
      int n = 0;
      for (int g = 1; g < branchOff.length; g++) {
        if (branchOff[g] == Integer.MAX_VALUE) {
          continue;
        }
        plies[n++] = branchOff[g];
      }
      // Games without a position of their own: those all of whose positions others reach too, and
      // those not counted
      gamesNeverBranching = games - n;
      Arrays.sort(plies, 0, n);
      branchOffPercentiles = new int[percentiles.length];
      for (int p = 0; p < percentiles.length; p++) {
        branchOffPercentiles[p] = n == 0 ? 0 : plies[(int) ((long) (n - 1) * percentiles[p] / 100)];
      }
    }

    /** The bits of n sorted ids below u, stored as Elias-Fano. */
    static double eliasFanoBits(long n, long u) {
      int low = n >= u ? 0 : (int) Math.floor(Math.log((double) u / n) / Math.log(2));
      return n * (2.0 + low);
    }

    void report(PrintWriter out) {
      out.printf(Locale.ROOT, "Position index statistics for %s%n%n", database);
      out.printf(Locale.ROOT, "Records                     %,15d%n", records);
      out.printf(Locale.ROOT, "Games indexed               %,15d%n", games);
      out.printf(Locale.ROOT, "  from a setup position     %,15d%n", setupGames);
      out.printf(Locale.ROOT, "Skipped: guiding texts      %,15d%n", texts);
      out.printf(Locale.ROOT, "Skipped: analyses           %,15d%n", analyses);
      out.printf(Locale.ROOT, "Skipped: deleted games      %,15d%n", deleted);
      out.printf(Locale.ROOT, "Skipped: moves not a game   %,15d%n", notMoves);
      out.printf(Locale.ROOT, "Skipped: Chess960           %,15d%n", chess960);
      out.printf(Locale.ROOT, "Skipped: undecodable        %,15d%n", undecodable);
      out.printf(
          Locale.ROOT,
          "Decoding                    %,15.0f s (%,.0f games/s)%n%n",
          decodeSeconds,
          games / decodeSeconds);

      out.printf(Locale.ROOT, "Positions in main lines     %,15d  (%.1f per game)%n", occurrences, (double) occurrences / games);
      out.printf(Locale.ROOT, "  repeated within a game    %,15d%n", repeated);
      out.printf(Locale.ROOT, "(position, game) entries    %,15d%n", entries);
      out.printf(Locale.ROOT, "Distinct positions          %,15d%n", distinct);
      out.printf(Locale.ROOT, "  reached by one game       %,15d  (%.1f%%)%n", singletons, pct(singletons, distinct));
      out.printf(Locale.ROOT, "  reached by several        %,15d  (%.1f%%, %,d entries)%n", distinct - singletons, pct(distinct - singletons, distinct), entries - singletons);
      out.printf(Locale.ROOT, "  the most games at one     %,15d%n", largestPosition);
      out.printf(Locale.ROOT, "Expected hash collisions    %15.4f  (pairs, n^2 / 2^65)%n%n", (double) distinct * distinct / Math.pow(2, 65));

      out.println("Positions by games reaching them");
      out.println("  at least        positions          entries");
      for (int t = 0; t < THRESHOLDS.length; t++) {
        out.printf(Locale.ROOT, "  %,8d  %,15d  %,15d%n", THRESHOLDS[t], positionsAtLeast[t], entriesAtLeast[t]);
      }
      out.println();
      out.printf(Locale.ROOT, "(position, move) groups     %,15d%n", moveGroups);
      out.printf(Locale.ROOT, "  of shared positions       %,15d%n", moveGroupsOfShared);
      out.printf(Locale.ROOT, "Games ending at a position  %,15d%n", lastPositions);
      out.println("(position, move) groups by games");
      for (int t = 0; t < THRESHOLDS.length; t++) {
        out.printf(Locale.ROOT, "  at least %,8d  %,15d%n", THRESHOLDS[t], moveGroupsAtLeast[t]);
      }
      out.println();

      out.println("Where games leave the positions other games reach (ply of the first position");
      out.println("only that game reaches)");
      for (int p = 0; p < percentiles.length; p++) {
        out.printf(Locale.ROOT, "  %2d%% of games by ply  %4d%n", percentiles[p], branchOffPercentiles[p]);
      }
      out.printf(Locale.ROOT, "  games never leaving them  %,13d%n", gamesNeverBranching);
      out.printf(Locale.ROOT, "Shared positions reached after a game left them%n");
      out.printf(Locale.ROOT, "  entries                   %,15d%n", sharedOccurrencesBeyond);
      out.printf(Locale.ROOT, "  positions, some games     %,15d%n", sharedPositionsSomeBeyond);
      out.printf(Locale.ROOT, "  positions, only so        %,15d%n%n", sharedPositionsOnlyBeyond);

      long shared = distinct - singletons;
      double keyBitsAll = eliasFanoKeyBits(distinct);
      double keyBitsShared = eliasFanoKeyBits(shared);
      int idBits = 32 - Integer.numberOfLeadingZeros(gameIdUniverse);
      out.println("Estimated sizes");
      out.printf(Locale.ROOT, "  plain (8 B hash + 4 B game + 2 B move) per entry %s%n", mb(entries * 14.0));
      out.printf(Locale.ROOT, "  shared positions: keys, Elias-Fano (%.1f bits)   %s%n", keyBitsShared / Math.max(1, shared), mb(keyBitsShared / 8));
      out.printf(Locale.ROOT, "  shared positions: keys, plain 8 B                %s%n", mb(shared * 8.0));
      out.printf(Locale.ROOT, "  shared positions: moves, 1 B + 2 B offset each   %s%n", mb(moveGroupsOfShared * 3.0));
      out.printf(Locale.ROOT, "  shared positions: game lists per move, E-F       %s%n", mb(moveListBits / 8));
      out.printf(Locale.ROOT, "  shared positions: game lists per position, E-F   %s%n", mb(positionListBits / 8));
      out.printf(Locale.ROOT, "  single-game positions: E-F key + %d-bit game id  %s%n", idBits, mb(singletons * (keyBitsAll / distinct + idBits) / 8));
      out.printf(Locale.ROOT, "  single-game positions: game id + 16-bit check    %s%n", mb(singletons * (idBits + 16 + 1.5) / 8));
      out.printf(Locale.ROOT, "  branch-off positions only: 8 B key + 4 B game    %s%n", mb(games * 12.0));
      for (int t = 0; t < THRESHOLDS.length; t++) {
        if (THRESHOLDS[t] >= 20 && THRESHOLDS[t] <= 1000) {
          out.printf(Locale.ROOT, "  move stats at >= %,5d games, 40 B each          %s%n", THRESHOLDS[t], mb(moveGroupsAtLeast[t] * 40.0));
        }
      }
      out.printf(Locale.ROOT, "  game facts, 16 B per game                        %s%n%n", mb(games * 16.0));
      out.printf(Locale.ROOT, "Counting (sorting %d buckets, the largest %,d entries) %,.0f s%n", BUCKETS, largestBucket, countSeconds);
      out.flush();
    }

    static double eliasFanoKeyBits(long n) {
      return n == 0 ? 0 : n * (2.0 + Math.floor(64 - Math.log(n) / Math.log(2)));
    }

    static double pct(long a, long b) {
      return b == 0 ? 0 : 100.0 * a / b;
    }

    static String mb(double bytes) {
      return String.format(Locale.ROOT, "%,10.0f MB", bytes / 1e6);
    }
  }
}
