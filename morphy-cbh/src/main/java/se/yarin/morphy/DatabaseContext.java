package se.yarin.morphy;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

import com.googlecode.concurentlocks.ReadWriteUpdateLock;
import com.googlecode.concurentlocks.ReentrantReadWriteUpdateLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The DatabaseContext is a mutable object coordinating database locking and instrumentation.
 *
 * <p>There are three types of locks: - Read - Upgradable read - Write
 *
 * <p>When a new database transaction starts, it acquires the upgradable read lock. At most one
 * thread can have this lock at the same time. Other threads can still acquire the ordinary read
 * lock. When the transaction is ready to commit, the lock is upgraded to a write lock (blocking if
 * necessary for existing read locks to be released). Other threads are not able to acquire read
 * locks during this time.
 *
 * <p>Simple read operations (e.g. get a game by id, fetch entity data by id) can use optimistic
 * read locks and then validating the lock after having read the data. This is not recommended for
 * read operations that iterate over data, e.g. fetch an entity by name or iterate over a set of
 * games.
 */
public class DatabaseContext {
  private final @NotNull DatabaseConfig config;
  private final @NotNull ReadWriteUpdateLock lock;
  private final @NotNull Instrumentation instrumentation;

  private final @NotNull AtomicInteger currentVersion;

  public DatabaseContext() {
    this(null);
  }

  public DatabaseContext(@Nullable DatabaseConfig config) {
    this.lock = new ReentrantReadWriteUpdateLock();
    this.currentVersion = new AtomicInteger(0);
    this.instrumentation = new Instrumentation();
    this.config = config == null ? new DatabaseConfig() : config;
  }

  public @NotNull DatabaseConfig config() {
    return config;
  }

  public Instrumentation instrumentation() {
    return instrumentation;
  }

  public int currentVersion() {
    return currentVersion.get();
  }

  public int bumpVersion() {
    return currentVersion.incrementAndGet();
  }

  public enum DatabaseLock {
    READ,
    UPDATE,
    WRITE
  }

  private @NotNull Lock getLock(@NotNull DatabaseLock lockType) {
    return switch (lockType) {
      case READ -> this.lock.readLock();
      case WRITE -> this.lock.writeLock();
      case UPDATE -> this.lock.updateLock();
    };
  }

  public void acquireLock(@NotNull DatabaseLock lockType) {
    Lock lock = getLock(lockType);
    long timeout =
        lockType == DatabaseLock.READ
            ? config.readLockWaitTimeoutInSeconds()
            : config.writeLockWaitTimeoutInSeconds();
    if (timeout == 0) {
      lock.lock();
    } else if (timeout < 0) {
      if (!lock.tryLock()) {
        throw new IllegalStateException("Failed to acquire lock");
      }
    } else {
      try {
        if (!lock.tryLock(timeout, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Failed to acquire lock");
        }
      } catch (InterruptedException e) {
        throw new IllegalStateException("Failed to acquire lock; thread was interrupted");
      }
    }
  }

  public void releaseLock(@NotNull DatabaseLock lockType) {
    getLock(lockType).unlock();
  }
}
