package se.yarin.morphy;

import java.util.concurrent.atomic.AtomicInteger;

import com.googlecode.concurentlocks.ReadWriteUpdateLock;
import com.googlecode.concurentlocks.ReentrantReadWriteUpdateLock;

/**
 * The DatabaseContext is a mutable object coordinating database locking and instrumentation.
 *
 * There are three types of locks:
 *  - Read
 *  - Upgradable read
 *  - Write
 *
 * When a new database transaction starts, it acquires the upgradable read lock. At most one thread
 * can have this lock at the same time. Other threads can still acquire the ordinary read lock.
 * When the transaction is ready to commit, the lock is upgraded to a write lock (blocking if necessary
 * for existing read locks to be released). Other threads are not able to acquire read locks during this time.
 *
 * Simple read operations (e.g. get a game by id, fetch entity data by id) can use optimistic read locks
 * and then validating the lock after having read the data.
 * This is not recommended for read operations that iterate over data, e.g. fetch an entity by name
 * or iterate over a set of games.
 */
public class DatabaseContext {
    private static final long DEFAULT_WRITE_LOCK_WAIT_TIMEOUT_IN_SECONDS = 5;
    private static final long DEFAULT_READ_LOCK_WAIT_TIMEOUT_IN_SECONDS = 5;

    private final ReadWriteUpdateLock lock;
    private final long writeLockWaitTimeoutInSeconds;
    private final long readLockWaitTimeoutInSeconds;
    private final AtomicInteger currentVersion;

    public DatabaseContext() {
        this(DEFAULT_READ_LOCK_WAIT_TIMEOUT_IN_SECONDS, DEFAULT_WRITE_LOCK_WAIT_TIMEOUT_IN_SECONDS);
    }

    public DatabaseContext(long readLockWaitTimeoutInSeconds, long writeLockWaitTimeoutInSeconds) {
        this.readLockWaitTimeoutInSeconds = readLockWaitTimeoutInSeconds;
        this.writeLockWaitTimeoutInSeconds = writeLockWaitTimeoutInSeconds;
        this.lock = new ReentrantReadWriteUpdateLock();
        this.currentVersion = new AtomicInteger(0);
    }

    public int currentVersion() {
        return currentVersion.get();
    }

    public void bumpVersion() {
        currentVersion.incrementAndGet();
    }

    public ReadWriteUpdateLock lock() {
        return lock;
    }
}
