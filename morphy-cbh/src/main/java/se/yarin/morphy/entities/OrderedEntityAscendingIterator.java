package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.entities.filters.EntityFilter;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class OrderedEntityAscendingIterator<T extends Entity & Comparable<T>> implements Iterator<T> {

    private @NotNull EntityIndexTransaction<T>.NodePath current;
    private final int stopId;
    private final @Nullable EntityFilter<T> filter;
    private @Nullable T nextItem;

    OrderedEntityAscendingIterator(@NotNull EntityIndexTransaction<T>.NodePath start, int stopId, @Nullable EntityFilter<T> filter) {
        this.current = start;
        this.stopId = stopId;
        this.filter = filter;

        setupNextItem();
    }

    private void setupNextItem() {
        while (!current.isEnd() && current.getEntityId() != stopId) {
            if (filter == null || filter.matchesSerialized(current.getNode().getSerializedEntity())) {
                nextItem = current.getEntity();
                if (filter == null || filter.matches(nextItem)) {
                    this.current = this.current.successor();
                    return;
                }
            }
            this.current = this.current.successor();
        }
        nextItem = null;
    }

    @Override
    public boolean hasNext() {
        current.ensureTransactionIsOpen();
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
