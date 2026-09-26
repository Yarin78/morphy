package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.chessbase.DatabaseLocks;

public abstract class TransactionBase implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(TransactionBase.class);

  private final @NotNull DatabaseLocks.Lock lock;
  private final @NotNull DatabaseContext context;

  // If the transaction is closed, no further operations can be done to it
  private boolean closed;

  protected TransactionBase(
      @NotNull DatabaseLocks.Lock lock, @NotNull DatabaseContext context) {
    context.acquireLock(lock);

    this.lock = lock;
    this.context = context;
  }

  public boolean isClosed() {
    return closed;
  }

  public void acquireLock(@NotNull DatabaseLocks.Lock lock) {
    context.acquireLock(lock);
  }

  public void releaseLock(@NotNull DatabaseLocks.Lock lock) {
    context.releaseLock(lock);
  }

  public void ensureTransactionIsOpen() {
    if (isClosed()) {
      throw new IllegalStateException("The transaction is closed");
    }
  }

  public void close() {
    if (!isClosed()) {
      closed = true;
      context.releaseLock(lock);
    }
  }
}
