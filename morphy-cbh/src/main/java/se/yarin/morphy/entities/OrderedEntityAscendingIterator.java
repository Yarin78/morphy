package se.yarin.morphy.entities;

import lombok.NonNull;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class OrderedEntityAscendingIterator<T extends Entity & Comparable<T>> implements Iterator<T> {

    // Invariant: current.node is the next entity to be returned
    // If current.isEnd(), there are no more entities to be returned
    private NodePath<T> current;
    private final int stopId;

    OrderedEntityAscendingIterator(@NonNull NodePath<T> start, int stopId) {
        this.current = start;
        this.stopId = stopId;
    }

    @Override
    public boolean hasNext() {
        return !current.isEnd() && current.getEntityId() != stopId;
    }

    @Override
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException("End of entity iteration reached");
        }
        T entity = current.getEntity();
        this.current = this.current.successor();
        return entity;
    }
}
