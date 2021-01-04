package se.yarin.cbhlib.storage;

import se.yarin.cbhlib.entities.Entity;

/**
 * An entity node. This class is immutable.
 */
public interface EntityNode<T extends Entity & Comparable<T>> {
    int getEntityId();
    int getLeftEntityId();
    int getRightEntityId();
    int getHeightDif();
    T getEntity();
    boolean isDeleted();
    EntityNode<T> update(int newLeftEntityId, int newRightEntityId, int newHeightDif);
}
