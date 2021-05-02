package se.yarin.morphy;

public class DatabaseConfig {
    // The timeout config determines how the code should behave when trying to acquire locks
    // * A negative value means the lock action will fail immediately if the lock is taken
    // * A zero value means the lock action will block forever until the lock is released
    // * A positive value means the lock action will wait at most that number of seconds for the lock to be released

    private long writeLockWaitTimeoutInSeconds = 5;
    private long readLockWaitTimeoutInSeconds = 5;

    public long writeLockWaitTimeoutInSeconds() {
        return writeLockWaitTimeoutInSeconds;
    }

    public void setWriteLockWaitTimeoutInSeconds(long writeLockWaitTimeoutInSeconds) {
        this.writeLockWaitTimeoutInSeconds = writeLockWaitTimeoutInSeconds;
    }

    public long readLockWaitTimeoutInSeconds() {
        return readLockWaitTimeoutInSeconds;
    }

    public void setReadLockWaitTimeoutInSeconds(long readLockWaitTimeoutInSeconds) {
        this.readLockWaitTimeoutInSeconds = readLockWaitTimeoutInSeconds;
    }
}
