package se.yarin.morphy.positions;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.jetbrains.annotations.NotNull;
import se.yarin.chess.Date;
import se.yarin.chess.GameResult;
import se.yarin.morphy.api.GameFacts;

/**
 * The facts of every game in an index, two longs per game id: what the statistics of a position's
 * moves and the sorting of its games need, kept in memory.
 *
 * <p>The first long holds the result's ordinal plus one (0 for no game) in bits 0-3, the date as
 * year·512 + month·32 + day in bits 4-24, White's Elo in bits 25-36 and Black's in bits 37-48.
 * The second holds White's player id plus one in the low 32 bits and Black's in the high, 0 for
 * none.
 */
public final class GameFactsTable {
  private final long[] facts;

  private GameFactsTable(long[] facts) {
    this.facts = facts;
  }

  /** An empty table for the games up to an id. */
  static @NotNull GameFactsTable forGames(int maxId) {
    return new GameFactsTable(new long[2 * (maxId + 1)]);
  }

  /** The highest game id there can be facts of. */
  public int maxId() {
    return facts.length / 2 - 1;
  }

  void set(int id, @NotNull GameFacts f) {
    Date date = f.date();
    long packedDate =
        (Math.min(Math.max(date.year(), 0), 4095) << 9)
            | (Math.min(Math.max(date.month(), 0), 15) << 5)
            | Math.min(Math.max(date.day(), 0), 31);
    facts[2 * id] =
        (f.result().ordinal() + 1)
            | (packedDate << 4)
            | ((long) elo(f.whiteElo()) << 25)
            | ((long) elo(f.blackElo()) << 37);
    facts[2 * id + 1] = player(f.whitePlayerId()) | (player(f.blackPlayerId()) << 32);
  }

  private static int elo(int elo) {
    return elo > 0 && elo < 4096 ? elo : 0;
  }

  private static long player(long id) {
    return id >= 0 && id < 0xFFFF_FFFFL ? id + 1 : 0;
  }

  /** Whether there are facts of a game: it's a game the index holds. */
  public boolean exists(int id) {
    return id > 0 && id <= maxId() && (facts[2 * id] & 15) != 0;
  }

  public @NotNull GameResult result(int id) {
    return GameResult.values()[(int) (facts[2 * id] & 15) - 1];
  }

  /** The date, as a number that sorts in time: year·512 + month·32 + day, 0 when unknown. */
  public int sortableDate(int id) {
    return (int) ((facts[2 * id] >>> 4) & ((1 << 21) - 1));
  }

  /** The year, 0 when unknown. */
  public int year(int id) {
    return sortableDate(id) >>> 9;
  }

  /** White's Elo, 0 if none. */
  public int whiteElo(int id) {
    return (int) ((facts[2 * id] >>> 25) & 4095);
  }

  /** Black's Elo, 0 if none. */
  public int blackElo(int id) {
    return (int) ((facts[2 * id] >>> 37) & 4095);
  }

  /** White's player id, -1 if none. */
  public long whitePlayer(int id) {
    return (facts[2 * id + 1] & 0xFFFF_FFFFL) - 1;
  }

  /** Black's player id, -1 if none. */
  public long blackPlayer(int id) {
    return (facts[2 * id + 1] >>> 32) - 1;
  }

  /** The newest year of the games, 0 if no game has one. */
  int newestYear() {
    int newest = 0;
    for (int id = 1; id <= maxId(); id++) {
      if (exists(id)) {
        newest = Math.max(newest, year(id));
      }
    }
    return newest;
  }

  void write(@NotNull Path file) throws IOException {
    try (FileChannel channel =
        FileChannel.open(
            file,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE)) {
      ByteBuffer buf = ByteBuffer.allocate(1 << 20);
      for (long f : facts) {
        if (!buf.hasRemaining()) {
          IndexFiles.writeFully(channel, buf.flip());
          buf.clear();
        }
        buf.putLong(f);
      }
      IndexFiles.writeFully(channel, buf.flip());
    }
  }

  static @NotNull GameFactsTable read(@NotNull Path file) throws IOException {
    return new GameFactsTable(IndexFiles.readLongs(file));
  }
}
