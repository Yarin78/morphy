package se.yarin.cbhlib.storage;

import lombok.NonNull;
import se.yarin.cbhlib.entities.Entity;

import java.io.IOException;
import java.util.List;

public abstract class EntityNodeStorageBase<T extends Entity & Comparable<T>> {

    private EntityNodeStorageMetadata metadata;

    public EntityNodeStorageBase(@NonNull EntityNodeStorageMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * Gets the entity node for the specified entity id.
     * @param entityId the id of the entity node to get
     * @return the entity node
     * @throws IOException if some IO error occurred
     */
    public abstract EntityNode<T> getEntityNode(int entityId) throws IOException;

    /**
     * Gets all entity node in the specified range.
     * @param startIdInclusive the id of the first entity to get
     * @param endIdExclusive the id of the first entity <i>not</i> to get
     * @return a list of all entities between startIdInclusive and endIdExclusive
     * @throws IOException if some IO error occurred
     */
    public abstract List<EntityNode<T>> getEntityNodes(int startIdInclusive, int endIdExclusive)
        throws IOException;

    /**
     * Puts an entity node in the storage
     * @param node the node to put. The entityId must be set.
     * @throws IOException if some IO error occurred
     */
    public abstract void putEntityNode(@NonNull EntityNode<T> node) throws IOException;

    public abstract EntityNode<T> createNode(int entityId, T entity);

    /**
     * Closes the storage
     */
    public abstract void close() throws IOException;

    public int getRootEntityId() {
        return this.metadata.getRootEntityId();
    }

    public int getNumEntities() {
        return this.metadata.getNumEntities();
    }

    public int getFirstDeletedEntityId() {
        return this.metadata.getFirstDeletedEntityId();
    }

    public int getCapacity() {
        return this.metadata.getCapacity();
    }

    public int getVersion() {
        return this.metadata.getVersion();
    }

    public EntityNodeStorageMetadata getMetadata() {
        return this.metadata;
    }

    public void setMetadata(EntityNodeStorageMetadata metadata) throws IOException {
        this.metadata = metadata;
    }

    /**
     * Returns a TreePath to the first node which does not compare less than entity, or null if no such node exists.
     * If nodes exists with that compares equally to entity, the first of those nodes will be returned.
     */
    public @NonNull TreePath<T> lowerBound(@NonNull T entity) throws IOException {
        return lowerBound(entity, getRootEntityId(), null);
    }

    private @NonNull TreePath<T> lowerBound(@NonNull T entity, int currentId, TreePath<T> path) throws IOException {
        if (currentId < 0) {
            return TreePath.end(this);
        }

        EntityNode<T> node = getEntityNode(currentId);
        T current = node.getEntity();
        int comp = entity.compareTo(current);

        path = new TreePath<>(this, currentId, path);
        if (comp <= 0) {
            TreePath<T> left = lowerBound(entity, node.getLeftEntityId(), path);
            return left.isEnd() ? path : left;
        } else {
            return lowerBound(entity, node.getRightEntityId(), path);
        }
    }

    /**
     * Returns a TreePath to the first node which compares greater than entity, or null if no such node exists.
     */
    public @NonNull TreePath<T> upperBound(@NonNull T entity) throws IOException {
        return upperBound(entity, getRootEntityId(), null);
    }

    private @NonNull TreePath<T> upperBound(@NonNull T entity, int currentId, TreePath<T> path) throws IOException {
        if (currentId < 0) {
            return TreePath.end(this);
        }

        EntityNode<T> node = getEntityNode(currentId);
        T current = node.getEntity();
        int comp = entity.compareTo(current);

        path = new TreePath<>(this, currentId, path);
        if (comp < 0) {
            TreePath<T> left = upperBound(entity, node.getLeftEntityId(), path);
            return left.isEnd() ? path : left;
        } else {
            return upperBound(entity, node.getRightEntityId(), path);
        }
    }
}
