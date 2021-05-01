package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class OrderedEntityDescendingIterator<T extends Entity & Comparable<T>> implements Iterator<T> {
    // Invariant: current.node is the next entity to be returned
    // If current.isBegin(), there are no more entities to be returned
    private @NotNull EntityIndexTransaction<T>.NodePath current;

    OrderedEntityDescendingIterator(@NotNull EntityIndexTransaction<T>.NodePath start) {
        this.current = start;
    }

    @Override
    public boolean hasNext() {
        current.ensureTransactionIsOpen();
        return !this.current.isBegin();
    }

    @Override
    public @NotNull T next() {
        if (!hasNext()) {
            throw new NoSuchElementException("End of entity iteration reached");
        }
        this.current = this.current.predecessor();
        return current.getEntity();
    }
}
