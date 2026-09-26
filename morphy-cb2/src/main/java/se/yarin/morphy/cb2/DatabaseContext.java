package se.yarin.morphy.cb2;

import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.chessbase.DatabaseLocks;

/**
 * Coordinates the transactions on one database: the locks they take, and a version that each
 * commit increases, so a write transaction can tell whether the database changed under it.
 *
 * <p>A read transaction holds the read lock. A write transaction holds the update lock, which one
 * thread at a time can hold while others read, and upgrades it to the write lock to commit.
 */
public final class DatabaseContext {
  private final @NotNull DatabaseLocks locks = new DatabaseLocks();
  private final @NotNull AtomicInteger version = new AtomicInteger();
  private final long readLockTimeoutSeconds;
  private final long writeLockTimeoutSeconds;

  /** A context waiting at most 5 seconds for a lock. */
  public DatabaseContext() {
    this(5, 5);
  }

  /**
   * A context with given lock timeouts: a negative timeout fails at once if the lock is taken, 0
   * waits forever, and a positive one waits at most that many seconds.
   */
  public DatabaseContext(long readLockTimeoutSeconds, long writeLockTimeoutSeconds) {
    this.readLockTimeoutSeconds = readLockTimeoutSeconds;
    this.writeLockTimeoutSeconds = writeLockTimeoutSeconds;
  }

  public void acquire(@NotNull DatabaseLocks.Lock lock) {
    locks.acquire(
        lock, lock == DatabaseLocks.Lock.READ ? readLockTimeoutSeconds : writeLockTimeoutSeconds);
  }

  public void release(@NotNull DatabaseLocks.Lock lock) {
    locks.release(lock);
  }

  /** The version of the database, increased by every commit. */
  public int version() {
    return version.get();
  }

  int bumpVersion() {
    return version.incrementAndGet();
  }
}
