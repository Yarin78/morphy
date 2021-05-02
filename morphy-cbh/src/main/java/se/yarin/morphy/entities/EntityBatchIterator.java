package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class EntityBatchIterator<T extends Entity & Comparable<T>> implements Iterator<T> {
    private static final int BATCH_SIZE = 1000;

    private final @NotNull EntityIndexReadTransaction<T> transaction;
    private @Nullable List<EntityNode> batch = new ArrayList<>();
    private int batchPos, nextBatchStart;

    public EntityBatchIterator(@NotNull EntityIndexReadTransaction<T> transaction, int startId) {
        // The batch reader only works in read transactions because it's optimized to read from
        // the storage directly, not supporting changes within a transaction
        this.transaction = transaction;
        this.nextBatchStart = startId;
        skipDeleted();
    }

    private void getNextBatch() {
        transaction.ensureTransactionIsOpen();

        int endId = Math.min(transaction.index().capacity(), nextBatchStart + BATCH_SIZE);
        if (nextBatchStart >= endId) {
            batch = null;
        } else {
            batch = transaction.index().storage.getItems(nextBatchStart, endId - nextBatchStart);
            nextBatchStart = endId;
        }
        batchPos = 0;
    }

    private void skipDeleted() {
        while (batch != null) {
            while (batchPos < batch.size()) {
                if (!batch.get(batchPos).isDeleted()) return;
                batchPos++;
            }
            getNextBatch();
        }
    }

    @Override
    public boolean hasNext() {
        return batch != null;
    }

    @Override
    public @NotNull T next() {
        if (!hasNext()) {
            throw new NoSuchElementException("End of entity iteration reached");
        }
        assert batch != null;
        EntityNode node = batch.get(batchPos++);
        skipDeleted();
        return transaction.index().resolveEntity(node);
    }
}
