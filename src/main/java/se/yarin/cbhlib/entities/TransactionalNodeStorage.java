package se.yarin.cbhlib.entities;

import lombok.NonNull;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A wrapper of an underlying {@link EntityNodeStorageBase}, storing new changes in memory
 */
public class TransactionalNodeStorage<T extends Entity & Comparable<T>> extends EntityNodeStorageBase<T> {
    private EntityNodeStorageBase<T> storage;
    private Map<Integer, EntityNode<T>> changes = new HashMap<>();

    public TransactionalNodeStorage(@NonNull EntityNodeStorageBase<T> storage) {
        this.storage = storage;
    }

    public Collection<EntityNode<T>> getChanges() {
        return changes.values();
    }

    @Override
    protected EntityNode<T> getEntityNode(int entityId) throws IOException {
        EntityNode<T> node = changes.get(entityId);
        if (node != null) {
            return node;
        }
        return storage.getEntityNode(entityId);
    }

    @Override
    protected List<EntityNode<T>> getEntityNodes(int startIdInclusive, int endIdExclusive) throws IOException {
        // Transactional operations don't need this method
        throw new UnsupportedOperationException();
    }

    @Override
    protected void putEntityNode(@NonNull EntityNode<T> node) throws IOException {
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

    @Override
    public void putMetadata(EntityNodeStorageMetadata metadata) throws IOException {
        // Updating metadata is handled directly by the transactional commit
        throw new UnsupportedOperationException();
    }
}
