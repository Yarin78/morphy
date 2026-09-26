package se.yarin.morphy;

import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.chessbase.DatabaseLocks;

/**
 * The DatabaseContext is a mutable object coordinating database locking and instrumentation.
 *
 * <p>Locking is done through {@link DatabaseLocks}, with the wait timeouts taken from the {@link
 * DatabaseConfig}.
 *
 * <p>Simple read operations (e.g. get a game by id, fetch entity data by id) can use optimistic
 * read locks and then validating the lock after having read the data. This is not recommended for
 * read operations that iterate over data, e.g. fetch an entity by name or iterate over a set of
 * games.
 */
public class DatabaseContext {
  private final @NotNull DatabaseConfig config;
  private final @NotNull DatabaseLocks locks;
  private final @NotNull Instrumentation instrumentation;

  private final @NotNull AtomicInteger currentVersion;

  public DatabaseContext() {
    this(null);
  }

  public DatabaseContext(@Nullable DatabaseConfig config) {
    this.locks = new DatabaseLocks();
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

  public void acquireLock(@NotNull DatabaseLocks.Lock lockType) {
    long timeout =
        lockType == DatabaseLocks.Lock.READ
            ? config.readLockWaitTimeoutInSeconds()
            : config.writeLockWaitTimeoutInSeconds();
    locks.acquire(lockType, timeout);
  }

  public void releaseLock(@NotNull DatabaseLocks.Lock lockType) {
    locks.release(lockType);
  }
}
