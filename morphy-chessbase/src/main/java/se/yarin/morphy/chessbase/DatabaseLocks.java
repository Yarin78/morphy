package se.yarin.morphy.chessbase;

import com.googlecode.concurentlocks.ReadWriteUpdateLock;
import com.googlecode.concurentlocks.ReentrantReadWriteUpdateLock;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * The locks coordinating transactions on one database.
 *
 * <p>There are three kinds of locks: read, upgradable read (update) and write. A read transaction
 * holds the read lock. A write transaction holds the update lock, which at most one thread can
 * hold at a time while others can still read, and upgrades it to the write lock when committing,
 * waiting for the readers to finish and keeping new ones out until the commit is done.
 */
public final class DatabaseLocks {

  /** A kind of lock. */
  public enum Lock {
    READ,
    UPDATE,
    WRITE
  }

  private final @NotNull ReadWriteUpdateLock lock = new ReentrantReadWriteUpdateLock();

  private java.util.concurrent.locks.Lock get(@NotNull Lock type) {
    return switch (type) {
      case READ -> lock.readLock();
      case UPDATE -> lock.updateLock();
      case WRITE -> lock.writeLock();
    };
  }

  /**
   * Acquires a lock.
   *
   * @param type the kind of lock
   * @param timeoutSeconds how long to wait for it: a negative value fails at once if the lock is
   *     taken, zero waits forever, and a positive value waits at most that many seconds
   * @throws IllegalStateException if the lock couldn't be acquired
   */
  public void acquire(@NotNull Lock type, long timeoutSeconds) {
    java.util.concurrent.locks.Lock l = get(type);
    if (timeoutSeconds == 0) {
      l.lock();
    } else if (timeoutSeconds < 0) {
      if (!l.tryLock()) {
        throw new IllegalStateException("Failed to acquire lock");
      }
    } else {
      try {
        if (!l.tryLock(timeoutSeconds, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Failed to acquire lock");
        }
      } catch (InterruptedException e) {
        throw new IllegalStateException("Failed to acquire lock; thread was interrupted");
      }
    }
  }

  /** Releases a lock held by the current thread. */
  public void release(@NotNull Lock type) {
    get(type).unlock();
  }
}
