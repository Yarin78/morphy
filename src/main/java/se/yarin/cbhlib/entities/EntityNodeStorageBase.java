package se.yarin.cbhlib.entities;

import lombok.NonNull;

import java.io.IOException;
import java.util.List;

abstract class EntityNodeStorageBase<T extends Entity & Comparable<T>> {

    private EntityNodeStorageMetadata metadata;

    EntityNodeStorageBase(@NonNull EntityNodeStorageMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * Gets the entity node for the specified entity id.
     * @param entityId the id of the entity node to get
     * @return the entity node
     * @throws IOException if some IO error occurred
     */
    protected abstract EntityNode<T> getEntityNode(int entityId) throws IOException;

    /**
     * Gets all entity node in the specified range.
     * @param startIdInclusive the id of the first entity to get
     * @param endIdExclusive the id of the first entity <i>not</i> to get
     * @return a list of all entities between startIdInclusive and endIdExclusive
     * @throws IOException if some IO error occurred
     */
    protected abstract List<EntityNode<T>> getEntityNodes(int startIdInclusive, int endIdExclusive)
        throws IOException;

    /**
     * Puts an entity node in the storage
     * @param node the node to put. The entityId must be set.
     * @throws IOException if some IO error occurred
     */
    protected abstract void putEntityNode(@NonNull EntityNode<T> node) throws IOException;

    abstract EntityNode<T> createNode(int entityId, T entity);

    /**
     * Closes the storage
     */
    abstract void close() throws IOException;

    int getRootEntityId() {
        return this.metadata.getRootEntityId();
    }

    int getNumEntities() {
        return this.metadata.getNumEntities();
    }

    int getFirstDeletedEntityId() {
        return this.metadata.getFirstDeletedEntityId();
    }

    int getCapacity() {
        return this.metadata.getCapacity();
    }

    int getVersion() {
        return this.metadata.getVersion();
    }

    EntityNodeStorageMetadata getMetadata() {
        return this.metadata;
    }

    void setMetadata(EntityNodeStorageMetadata metadata) throws IOException {
        this.metadata = metadata;
    }

    /**
     * Searches the tree for a specific entity. Returns a path from the root
     * to the searched entity.
     * If the entity doesn't exist in the tree, the path ends at the node in the
     * tree where the entity can be inserted.
     * @param currentId the start node to search from
     * @param path the path searched for so far
     * @param entity the entity to search for
     * @return the most recent node in the path
     * @throws IOException if an IO error occurred when searching in the tree
     */
    TreePath<T> treeSearch(int currentId, TreePath<T> path, @NonNull T entity) throws IOException {
        if (currentId < 0) {
            return path;
        }

        EntityNode<T> node = getEntityNode(currentId);
        T current = node.getEntity();
        int comp = entity.compareTo(current);

        path = new TreePath<>(comp, node, path);
        if (comp == 0) {
            return path;
        } else if (comp < 0) {
            return treeSearch(node.getLeftEntityId(), path, entity);
        } else {
            return treeSearch(node.getRightEntityId(), path, entity);
        }
    }
}
