package se.yarin.morphy.cb2;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.chessbase.DatabaseLocks;

/**
 * A transaction that only reads. It holds the read lock while open, so no commit can change the
 * database under it.
 */
public final class ReadTransaction extends DatabaseTransaction {

  public ReadTransaction(@NotNull Database2Cbh database) {
    super(database, DatabaseLocks.Lock.READ);
  }

  /** All records, in id order. */
  public @NotNull Stream<Game> stream() {
    return stream(1, count() + 1);
  }

  /**
   * The records in a range of ids, in id order.
   *
   * @param startId the first id, inclusive
   * @param endId the last id, exclusive
   */
  public @NotNull Stream<Game> stream(int startId, int endId) {
    return IntStream.range(Math.max(1, startId), Math.min(endId, count() + 1)).mapToObj(this::getGame);
  }

  /** All records, in id order. */
  public @NotNull Iterable<Game> iterable() {
    return () -> stream().iterator();
  }
}
