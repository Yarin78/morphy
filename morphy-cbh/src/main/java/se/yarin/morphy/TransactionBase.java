package se.yarin.morphy;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TransactionBase {
    private static final Logger log = LoggerFactory.getLogger(TransactionBase.class);

    private final @NotNull DatabaseContext.DatabaseLock lock;
    private final @NotNull DatabaseContext context;

    // If the transaction is closed, no further operations can be done to it
    private boolean closed;

    protected TransactionBase(@NotNull DatabaseContext.DatabaseLock lock, @NotNull DatabaseContext context) {
        context.acquireLock(lock);

        this.lock = lock;
        this.context = context;
    }

    public boolean isClosed() {
        return closed;
    }

    public void ensureTransactionIsOpen() {
        if (isClosed()) {
            throw new IllegalStateException("The transaction is closed");
        }
    }

    protected void closeTransaction() {
        closed = true;
        context.releaseLock(lock);
    }
}
