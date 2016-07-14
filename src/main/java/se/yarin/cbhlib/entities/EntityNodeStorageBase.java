package se.yarin.cbhlib.entities;

import lombok.NonNull;

import java.io.IOException;
import java.util.List;

public abstract class EntityNodeStorageBase<T extends Entity & Comparable<T>> {

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

    public abstract EntityNode<T> createNode(int entityId, T entity);

    /**
     * Closes the storage
     */
    public abstract void close() throws IOException;

    public abstract void putMetadata(EntityNodeStorageMetadata metadata) throws IOException;
}
