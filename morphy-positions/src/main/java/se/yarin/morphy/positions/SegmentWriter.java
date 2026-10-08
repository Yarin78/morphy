package se.yarin.morphy.positions;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Writes a segment, its positions given in increasing unsigned order of hash: those several games
 * reached to the shared files, with their moves, games and, for moves played in {@link
 * #STATS_THRESHOLD} games or more, statistics; and those one game reached to the single-game
 * files, as the rest of the hash, the move and the game, by a directory of the top bits of the
 * hash.
 *
 * <p>{@code shared.data} holds per position {@code varint(moves << 1 | whiteToMove)}, then per
 * move its 16-bit field ({@link IndexFiles#moveField}), its number of games (varint), a flag byte
 * (1 if the statistics follow), the statistics, and the game ids, each as the difference from the
 * one before (varints).
 */
final class SegmentWriter implements AutoCloseable {
  /** The fewest games of a move whose statistics are stored. */
  static final int STATS_THRESHOLD = 50;

  private final Path dir;
  private final @Nullable GameFactsTable facts;
  private final int recentSince;
  private final int directoryBits;
  private final int gameIdBytes;
  private final int hashBytes;
  private final int[] directory;
  private final DataOutputStream keys, offsets, sharedData, singleData;
  private final Bytes position = new Bytes();
  private long sharedOffset, shared, single;
  private long previous;
  private boolean first = true;

  /**
   * Starts a segment in a new directory.
   *
   * @param facts the facts of the games, which the statistics of the moves played often are worked
   *     out from; null to store none, in a segment that's to be merged
   * @param recentSince the first year of the recent games, in the statistics
   * @param expectedSingles about how many positions one game reached, to size the directory by
   * @param maxGameId the highest game id there can be
   */
  SegmentWriter(
      @NotNull Path dir,
      @Nullable GameFactsTable facts,
      int recentSince,
      long expectedSingles,
      int maxGameId)
      throws IOException {
    this.dir = dir;
    this.facts = facts;
    this.recentSince = recentSince;
    // Some 32 entries per value of the top bits
    this.directoryBits =
        Math.max(8, Math.min(24, 64 - Long.numberOfLeadingZeros(Math.max(1, expectedSingles / 32))));
    this.gameIdBytes = maxGameId < (1 << 24) ? 3 : 4;
    this.hashBytes = (64 - directoryBits + 7) / 8;
    this.directory = new int[(1 << directoryBits) + 1];
    Files.createDirectories(dir);
    keys = output(IndexFiles.SHARED_KEYS);
    offsets = output(IndexFiles.SHARED_OFFSETS);
    sharedData = output(IndexFiles.SHARED_DATA);
    singleData = output(IndexFiles.SINGLE_DATA);
  }

  private DataOutputStream output(String name) throws IOException {
    return new DataOutputStream(
        new BufferedOutputStream(Files.newOutputStream(dir.resolve(name)), 1 << 20));
  }

  /** Adds a position, after those added before in hash order. */
  void add(@NotNull PositionEntry entry) throws IOException {
    long hash = entry.hash();
    if (!first && Long.compareUnsigned(hash, previous) <= 0) {
      throw new IllegalStateException("Positions out of order");
    }
    first = false;
    previous = hash;
    if (entry.groups().size() == 1 && entry.groups().getFirst().gameIds().length == 1) {
      MoveGroup group = entry.groups().getFirst();
      for (int b = hashBytes - 1; b >= 0; b--) {
        singleData.write((int) (hash >>> (8 * b)));
      }
      singleData.writeShort(IndexFiles.moveField(group.moveCode(), entry.whiteToMove()));
      int game = group.gameIds()[0];
      for (int b = gameIdBytes - 1; b >= 0; b--) {
        singleData.write(game >>> (8 * b));
      }
      directory[(int) (hash >>> (64 - directoryBits)) + 1]++;
      single++;
      return;
    }
    position.clear();
    position.putVar(((long) entry.groups().size() << 1) | (entry.whiteToMove() ? 1 : 0));
    for (MoveGroup group : entry.groups()) {
      int[] ids = group.gameIds();
      position.putShort(IndexFiles.moveField(group.moveCode(), entry.whiteToMove()));
      position.putVar(ids.length);
      boolean stats =
          facts != null
              && ids.length >= STATS_THRESHOLD
              && group.moveCode() != IndexFiles.GAME_ENDED;
      position.put(stats ? 1 : 0);
      if (stats) {
        MoveStats.of(ids, facts, entry.whiteToMove(), recentSince).write(position);
      }
      int previousId = 0;
      for (int id : ids) {
        position.putVar(id - previousId);
        previousId = id;
      }
    }
    keys.writeLong(hash);
    offsets.writeLong(sharedOffset);
    sharedData.write(position.array(), 0, position.size());
    sharedOffset += position.size();
    shared++;
  }

  /** Finishes the segment: its directory, the end of the offsets and its metadata. */
  @Override
  public void close() throws IOException {
    offsets.writeLong(sharedOffset);
    for (DataOutputStream out : new DataOutputStream[] {keys, offsets, sharedData, singleData}) {
      out.close();
    }
    for (int k = 0; k < directory.length - 1; k++) {
      directory[k + 1] += directory[k];
    }
    IndexFiles.writeInts(dir.resolve(IndexFiles.SINGLE_DIRECTORY), directory);
    new SegmentMeta(shared, single, directoryBits, gameIdBytes)
        .write(dir.resolve(IndexFiles.SEGMENT_META));
  }
}
