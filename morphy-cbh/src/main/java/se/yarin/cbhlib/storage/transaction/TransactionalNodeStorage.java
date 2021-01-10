package se.yarin.cbhlib.storage.transaction;

import lombok.NonNull;
import se.yarin.cbhlib.entities.Entity;
import se.yarin.cbhlib.storage.EntityNode;
import se.yarin.cbhlib.storage.EntityNodeStorageBase;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A wrapper of an underlying {@link EntityNodeStorageBase}, storing new changes in memory.
 */
public class TransactionalNodeStorage<T extends Entity & Comparable<T>> extends EntityNodeStorageBase<T> {
    private EntityNodeStorageBase<T> storage;
    private Map<Integer, EntityNode<T>> changes = new HashMap<>();

    public TransactionalNodeStorage(@NonNull EntityNodeStorageBase<T> storage) {
        super(storage.getMetadata().clone());
        this.storage = storage;
    }

    Collection<EntityNode<T>> getChanges() {
        return changes.values();
    }

    @Override
    public EntityNode<T> getEntityNode(int entityId) {
        EntityNode<T> node = changes.get(entityId);
        if (node != null) {
            return node;
        }
        return storage.getEntityNode(entityId);
    }

    @Override
    public Collection<EntityNode<T>> getAllEntityNodes() {
        // Transactional operations don't need this method
        throw new UnsupportedOperationException();
    }

    @Override
    public List<EntityNode<T>> getEntityNodes(int startIdInclusive, int endIdExclusive) {
        // Transactional operations don't need this method
        throw new UnsupportedOperationException();
    }

    @Override
    public void putEntityNode(@NonNull EntityNode<T> node) {
        changes.put(node.getEntityId(), node);
    }

    @Override
    public EntityNode<T> createNode(int entityId, T entity) {
        return storage.createNode(entityId, entity);
    }

    @Override
    public void close() throws IOException {
        // Closing the underlying storage for a transaction is not a valid operation
        throw new UnsupportedOperationException();
    }


    void setRootEntityId(int entityId) {
        getMetadata().setRootEntityId(entityId);
    }

    void setNumEntities(int numEntities) {
        getMetadata().setNumEntities(numEntities);
    }

    void setFirstDeletedEntityId(int firstDeletedEntityId) {
        getMetadata().setFirstDeletedEntityId(firstDeletedEntityId);
    }

    void setCapacity(int capacity) {
        getMetadata().setCapacity(capacity);
    }
}
