package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class OrderedEntityAscendingIterator<T extends Entity & Comparable<T>> implements Iterator<T> {

    // Invariant: current.node is the next entity to be returned
    // If current.isEnd(), there are no more entities to be returned
    private @NotNull EntityIndexTransaction<T>.NodePath current;
    private final int stopId;

    OrderedEntityAscendingIterator(@NotNull EntityIndexTransaction<T>.NodePath start, int stopId) {
        this.current = start;
        this.stopId = stopId;
    }

    @Override
    public boolean hasNext() {
        current.ensureTransactionIsOpen();
        return !current.isEnd() && current.getEntityId() != stopId;
    }

    @Override
    public @NotNull T next() {
        if (!hasNext()) {
            throw new NoSuchElementException("End of entity iteration reached");
        }
        T entity = current.getEntity();
        this.current = this.current.successor();
        return entity;
    }
}
