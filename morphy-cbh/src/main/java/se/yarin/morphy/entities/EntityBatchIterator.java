package se.yarin.morphy.entities;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class EntityBatchIterator<T extends Entity & Comparable<T>> implements Iterator<T> {
    private static final int BATCH_SIZE = 1000;

    private final EntityIndex<T> index;
    private List<EntityNode> batch = new ArrayList<>();
    private int batchPos, nextBatchStart;

    public EntityBatchIterator(EntityIndex<T> index, int startId) {
        this.index = index;
        this.nextBatchStart = startId;
        skipDeleted();
    }

    private void getNextBatch() {
        int endId = Math.min(index.capacity(), nextBatchStart + BATCH_SIZE);
        if (nextBatchStart >= endId) {
            batch = null;
        } else {
            batch = index.storage.getItems(nextBatchStart, endId - nextBatchStart);
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
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException("End of entity iteration reached");
        }
        EntityNode node = batch.get(batchPos++);
        skipDeleted();
        return index.resolveEntity(node);
    }
}
