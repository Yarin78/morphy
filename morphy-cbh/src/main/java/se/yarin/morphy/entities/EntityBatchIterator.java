package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.filters.EntityFilter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class EntityBatchIterator<T extends Entity & Comparable<T>> implements Iterator<T> {
    private static final int BATCH_SIZE = 1000;

    private final @NotNull EntityIndexReadTransaction<T> transaction;
    private @Nullable List<EntityNode> batch = new ArrayList<>();
    private @Nullable EntityFilter<T> filter;
    private @Nullable T nextItem;
    private int batchPos, nextBatchStart;

    public EntityBatchIterator(@NotNull EntityIndexReadTransaction<T> transaction, int startId, @Nullable EntityFilter<T> filter) {
        // The batch reader only works in read transactions because it's optimized to read from
        // the storage directly, not supporting changes within a transaction
        this.transaction = transaction;
        this.nextBatchStart = startId;
        this.filter = filter;
        setupNextItem();
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

    private void setupNextItem() {
        while (batch != null) {
            while (batchPos < batch.size()) {
                EntityNode entityNode = batch.get(batchPos++);
                if (!entityNode.isDeleted() && (filter == null || filter.matchesSerialized(entityNode.getSerializedEntity()))) {
                    nextItem = transaction.index().resolveEntity(entityNode);
                    if (filter == null || filter.matches(nextItem)) {
                        return;
                    }
                }
            }
            getNextBatch();
        }
        nextItem = null;
    }

    @Override
    public boolean hasNext() {
        return nextItem != null;
    }

    @Override
    public @NotNull T next() {
        if (nextItem == null) {
            throw new NoSuchElementException("End of entity iteration reached");
        }
        T item = nextItem;
        setupNextItem();
        return item;
    }
}
