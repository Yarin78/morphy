package se.yarin.cbhlib.entities;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

class EntityStorageTransaction<T extends Entity & Comparable<T>> {
    private static final Logger log = LoggerFactory.getLogger(EntityStorageTransaction.class);

    private static final int ENTITY_DELETED = -999;

    private final TransactionalNodeStorage<T> nodeStorage;
    private final EntityNodeStorageBase<T> underlyingNodeStorage;
    private final EntityStorage<T> storage;
    private boolean committed = false;

    EntityStorageTransaction(@NonNull EntityStorage<T> storage,
                             @NonNull EntityNodeStorageBase<T> nodeStorage) {
        this.storage = storage;
        this.underlyingNodeStorage = nodeStorage;
        this.nodeStorage = new TransactionalNodeStorage<>(nodeStorage);
    }

    synchronized void commit() throws EntityStorageException, IOException {
        if (committed) {
            throw new IllegalStateException("The transaction has already been committed");
        }

        if (storage.getVersion() + 1 != nodeStorage.getVersion()) {
            throw new EntityStorageException("Storage has changed since the transaction was begun");
        }

        for (EntityNode<T> node : nodeStorage.getChanges()) {
            underlyingNodeStorage.putEntityNode(node);
        }
        underlyingNodeStorage.setMetadata(this.nodeStorage.getMetadata());

        // TODO: flush underlyingNodeStorage?
        if (log.isDebugEnabled()) {
            log.debug(String.format("Committed transaction containing %d node changes",
                    nodeStorage.getChanges().size()));
        }

        committed = true;
    }


    /**
     * Searches the tree for a specific entity. Returns a path from the root
     * to the searched entity.
     * If the entity doesn't exist in the tree, the path ends at the node in the
     * tree where the entity can be inserted.
     * @param entity the entity to search for
     * @return the most recent node in the path
     * @throws IOException if an IO error occurred when searching in the tree
     */
    private TreePath<T> treeSearch(@NonNull T entity) throws IOException {
        return nodeStorage.treeSearch(nodeStorage.getRootEntityId(), null, entity);
    }

    public T getEntity(int entityId) throws IOException {
        if (entityId < 0 || entityId >= nodeStorage.getCapacity()) {
            return null;
        }
        return nodeStorage.getEntityNode(entityId).getEntity();
    }

    public T getEntity(@NonNull T entity) throws IOException {
        TreePath<T> treePath = treeSearch(entity);
        if (treePath == null) {
            return null;
        }
        T foundEntity = treePath.getNode().getEntity();
        if (foundEntity.compareTo(entity) == 0) {
            return foundEntity;
        }
        return null;
    }

    int getNumEntities() {
        return nodeStorage.getNumEntities();
    }

    int getCapacity() {
        return nodeStorage.getCapacity();
    }


    /**
     * Adds a new entity to the storage. The id-field in the entity is ignored.
     * @param entity the entity to add
     * @return the id of the new entity
     * @throws EntityStorageException if another entity with the same key already exists
     */
    int addEntity(@NonNull T entity) throws EntityStorageException, IOException {
        if (committed) {
            throw new IllegalStateException("The transaction has already been committed");
        }

        int entityId;
        TreePath<T> result = treeSearch(entity);
        if (result != null && result.getCompare() == 0) {
            throw new EntityStorageException("An entity with the same key already exists");
        }

        if (nodeStorage.getFirstDeletedEntityId() >= 0) {
            // Replace a deleted entity
            entityId = nodeStorage.getFirstDeletedEntityId();
            nodeStorage.setFirstDeletedEntityId(nodeStorage.getEntityNode(entityId).getRightEntityId());
        } else {
            // Appended new entity to the end
            entityId = nodeStorage.getCapacity();
            nodeStorage.setCapacity(entityId + 1);
        }
        nodeStorage.setNumEntities(nodeStorage.getNumEntities() + 1);

        if (result == null) {
            nodeStorage.setRootEntityId(entityId);
        } else {
            EntityNode<T> node = result.getNode();
            if (result.getCompare() < 0) {
                result = new TreePath<>(-1, node.update(entityId, node.getRightEntityId(), node.getHeightDif()), result.getParent());
            } else {
                result = new TreePath<>(-1, node.update(node.getLeftEntityId(), entityId, node.getHeightDif()), result.getParent());
            }
            nodeStorage.putEntityNode(result.getNode());
        }

        EntityNode<T> z = nodeStorage.createNode(entityId, entity);
        nodeStorage.putEntityNode(z);

        for(; result != null; result = result.getParent()) {
            // result.node might contain an old version of the node (I think!?)
            EntityNode<T> x = nodeStorage.getEntityNode(result.getNode().getEntityId());
            EntityNode<T> g;
            int n;
            // BalanceFactor(X) has not yet been updated!
            if (z.getEntityId() == x.getRightEntityId()) { // The right subtree increases
                if (x.getHeightDif() > 0) { // X is right-heavy
                    // ===> the temporary BalanceFactor(X) == +2
                    // ===> rebalancing is required.
                    g = result.getParent() != null ? result.getParent().getNode() : null;
                    if (z.getHeightDif() < 0) {
                        n = rotateRightLeft(x.getEntityId());
                    } else {
                        n = rotateLeft(x.getEntityId());
                    }
                } else {
                    if (x.getHeightDif() < 0) {
                        nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), x.getRightEntityId(), 0));
                        break;
                    }
                    EntityNode<T> newX = x.update(x.getLeftEntityId(), x.getRightEntityId(), 1);
                    nodeStorage.putEntityNode(newX);
                    z = newX;
                    continue;
                }
            } else { // Z == left_child(X): the left subtree increases
                if (x.getHeightDif() < 0) { // X is left-heavy
                    g = result.getParent() != null ? result.getParent().getNode() : null;
                    if (z.getHeightDif() > 0) {
                        n = rotateLeftRight(x.getEntityId());
                    } else {
                        n = rotateRight(x.getEntityId());
                    }
                } else {
                    if (x.getHeightDif() > 0) {
                        nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), x.getRightEntityId(), 0));
                        break;
                    }
                    EntityNode<T> newX = x.update(x.getLeftEntityId(), x.getRightEntityId(), -1);
                    nodeStorage.putEntityNode(newX);
                    z = newX;
                    continue;
                }
            }

            if (g != null) {
                if (x.getEntityId() == g.getLeftEntityId()) {
                    nodeStorage.putEntityNode(g.update(n, g.getRightEntityId(), g.getHeightDif()));
                } else {
                    nodeStorage.putEntityNode(g.update(g.getLeftEntityId(), n, g.getHeightDif()));
                }
                break;
            } else {
                nodeStorage.setRootEntityId(n);
                break;
            }
        }

        return entityId;
    }

    /**
     * Updates an entity in the storage.
     * @param entityId the entity id to update.
     * @param entity the new entity. {@link Entity#getId()} will be ignored.
     * @throws EntityStorageException if another entity with the same key already exists
     */
    void putEntityById(int entityId, @NonNull T entity) throws EntityStorageException, IOException {
        if (committed) {
            throw new IllegalStateException("The transaction has already been committed");
        }

        if (entityId < 0 || entityId >= nodeStorage.getCapacity()) {
            throw new IllegalArgumentException(String.format("Can't put an entity with id %d when capacity is %d",
                    entityId, nodeStorage.getCapacity()));
        }
        EntityNode<T> oldNode = nodeStorage.getEntityNode(entityId);
        if (oldNode.isDeleted()) {
            throw new IllegalArgumentException("Can't replace a deleted entity");
        }
        if (oldNode.getEntity().compareTo(entity) == 0) {
            // The key is the same, so we don't have to update the tree
            EntityNode<T> newNode = nodeStorage.createNode(entityId, entity);
            newNode = newNode.update(oldNode.getLeftEntityId(), oldNode.getRightEntityId(), oldNode.getHeightDif());
            nodeStorage.putEntityNode(newNode);
        } else {
            // TODO: Do this in a transaction?
            deleteEntity(entityId);
            int newEntityId = addEntity(entity);
            assert entityId == newEntityId; // Important!
        }
    }

    /**
     * Updates an entity in the storage. The key fields of the entity will
     * determine which entity in the storage to update.
     * @param entity the new entity. {@link Entity#getId()} will be ignored.
     * @throws EntityStorageException if no existing entity with the key exists
     */
    void putEntityByKey(@NonNull T entity) throws EntityStorageException, IOException {
        if (committed) {
            throw new IllegalStateException("The transaction has already been committed");
        }

        TreePath<T> treePath = treeSearch(entity);
        if (treePath == null || treePath.getCompare() != 0) {
            throw new EntityStorageException("The entity doesn't exist in the storage");
        }
        EntityNode<T> node = treePath.getNode();
        int entityId = node.getEntityId();
        EntityNode<T> newNode = nodeStorage.createNode(entityId, entity);
        newNode = newNode.update(node.getLeftEntityId(), node.getRightEntityId(), node.getHeightDif());
        nodeStorage.putEntityNode(newNode);
    }

    /**
     * Deletes an entity from the storage.
     * @param entityId the id of the entity to delete
     * @return true if an entity was deleted; false if there was no entity with that id
     */
    boolean deleteEntity(int entityId) throws IOException, EntityStorageException {
        if (committed) {
            throw new IllegalStateException("The transaction has already been committed");
        }

        EntityNode<T> node = nodeStorage.getEntityNode(entityId);
        if (node.isDeleted()) {
            log.debug("Deleted entity with id " + entityId + " that was already deleted");
            return false;
        }

        // Find the node we want to delete in the tree
        TreePath<T> nodePath = treeSearch(node.getEntity());
        if (nodePath == null || nodePath.getCompare() != 0) {
            throw new EntityStorageException("Broken database structure; couldn't find the node to delete.");
        }
        return internalDeleteEntity(nodePath);
    }

    /**
     * Deletes an entity from the storage.
     * @param entity the entity key to delete
     * @return true if an entity was deleted; false if there was no entity with that key
     */
    boolean deleteEntity(T entity) throws IOException, EntityStorageException {
        if (committed) {
            throw new IllegalStateException("The transaction has already been committed");
        }

        TreePath<T> nodePath = treeSearch(entity);
        if (nodePath == null || nodePath.getCompare() != 0) {
            log.debug("Deleted entity didn't exist");
            return false;
        }
        return internalDeleteEntity(nodePath);
    }


    private boolean internalDeleteEntity(TreePath<T> nodePath) throws IOException {
        EntityNode<T> node = nodePath.getNode();
        int entityId = node.getEntityId();
        nodePath = nodePath.getParent();

        // Switch the node we want to delete with a successor node until it has at most one child
        // This will take at most one iteration, so we could simplify this
        while (node.getLeftEntityId() >= 0 && node.getRightEntityId() >= 0) {
            // Invariant: node is the node we want to delete, and it has two children
            // nodePath.node = the parent node
            // nodePath.compare < 0 if the deleted node is a left child, > 0 if a right child

            // Find successor node and replace it with this one
            TreePath<T> successorPath = nodeStorage.treeSearch(node.getRightEntityId(), null, node.getEntity());
            assert successorPath.getCompare() < 0; // Should always be a left child
            EntityNode<T> successorNode = successorPath.getNode();
            // successorPath.node = the node we want to move up and replace node
            successorPath = successorPath.getParent(); // successorPath.node may now equal node!!

            EntityNode<T> newNode = node.update(successorNode.getLeftEntityId(), successorNode.getRightEntityId(), successorNode.getHeightDif());
            int rid = node.getRightEntityId();
            if (rid == successorNode.getEntityId()) {
                rid = node.getEntityId();
            }
            EntityNode<T> newSuccessorNode = successorNode.update(node.getLeftEntityId(), rid, node.getHeightDif());
            replaceChild(nodePath, successorNode.getEntityId());
            if (successorPath != null) {
                replaceChild(successorPath, node.getEntityId());
            }
            nodeStorage.putEntityNode(newNode);
            nodeStorage.putEntityNode(newSuccessorNode);

            node = newNode;
            if (successorPath == null) {
                successorPath = new TreePath<>(1, newSuccessorNode, nodePath);
            } else {
                successorPath = successorPath.appendToTail(new TreePath<>(1, newSuccessorNode, nodePath));
            }

            nodePath = successorPath; // Won't work probably if parent to successor was node
        }

        // Now node has at most one child!
        // nodePath.node = the parent node
        // nodePath.compare < 0 if the deleted node is a left child, > 0 if a right child
        int onlyChild = node.getLeftEntityId() >= 0 ? node.getLeftEntityId() : node.getRightEntityId();
        replaceChild(nodePath, onlyChild);

        // Nothing should now point to the node we want to delete
        EntityNode<T> deletedNode = nodeStorage.createNode(entityId, null)
                .update(ENTITY_DELETED, nodeStorage.getFirstDeletedEntityId(), 0);

        nodeStorage.putEntityNode(deletedNode);
        nodeStorage.setFirstDeletedEntityId(entityId);
        nodeStorage.setNumEntities(nodeStorage.getNumEntities() - 1);


        // Retrace and rebalance tree
        EntityNode<T> g;
        for (EntityNode<T> x = nodePath == null ? null : nodeStorage.getEntityNode(nodePath.getNode().getEntityId()); x != null; x = g, nodePath = nodePath.getParent()){
            // The stored value in the path might be old
            g = nodePath.getParent() == null ? null : nodeStorage.getEntityNode(nodePath.getParent().getNode().getEntityId());
            int b, n;
            if (nodePath.getCompare() < 0) {
                if (x.getHeightDif() > 0) {
                    EntityNode<T> z = nodeStorage.getEntityNode(x.getRightEntityId());
                    b = z.getHeightDif();
                    if (b < 0) {
                        n = rotateRightLeft(x.getEntityId());
                    } else {
                        n = rotateLeft(x.getEntityId());
                    }
                } else {
                    if (x.getHeightDif() == 0) {
                        nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), x.getRightEntityId(), 1));
                        break;
                    }
                    nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), x.getRightEntityId(), 0));
                    continue;
                }
            } else {
                if (x.getHeightDif() < 0) {
                    EntityNode<T> z = nodeStorage.getEntityNode(x.getLeftEntityId());
                    b = z.getHeightDif();
                    if (b > 0) {
                        n = rotateLeftRight(x.getEntityId());
                    } else {
                        n = rotateRight(x.getEntityId());
                    }
                } else {
                    if (x.getHeightDif() == 0) {
                        nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), x.getRightEntityId(), -1));
                        break;
                    }
                    nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), x.getRightEntityId(), 0));
                    continue;
                }
            }

            if (g == null) {
                nodeStorage.setRootEntityId(n);
            } else {
                if (nodePath.getParent().getCompare() < 0) {
                    g = g.update(n, g.getRightEntityId(), g.getHeightDif());
                } else {
                    g = g.update(g.getLeftEntityId(), n, g.getHeightDif());
                }
                nodeStorage.putEntityNode(g);
                if (b == 0) break;
            }
        }

        return true;
    }

    private void replaceChild(TreePath<T> path, int newChildId) throws IOException {
        if (path == null) {
            // The root node has no parent
            nodeStorage.setRootEntityId(newChildId);
        } else {
            EntityNode<T> node = nodeStorage.getEntityNode(path.getNode().getEntityId());
            if (path.getCompare() < 0) {
                node = node.update(newChildId, node.getRightEntityId(), node.getHeightDif());
            } else {
                node = node.update(node.getLeftEntityId(), newChildId, node.getHeightDif());
            }
            nodeStorage.putEntityNode(node);
        }
    }

    // Rotates the tree rooted at nodeId to the left and returns the new root
    private int rotateLeft(int nodeId) throws IOException {
        EntityNode<T> x = nodeStorage.getEntityNode(nodeId);
        EntityNode<T> z = nodeStorage.getEntityNode(x.getRightEntityId());
        int newRightChildX = z.getLeftEntityId();
        int newLeftChildZ = x.getEntityId();
        int newXHeightDif = 0, newZHeightDif = 0;
        if (z.getHeightDif() == 0) {
            newXHeightDif = 1;
            newZHeightDif = -1;
        }
        nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), newRightChildX, newXHeightDif));
        nodeStorage.putEntityNode(z.update(newLeftChildZ, z.getRightEntityId(), newZHeightDif));
        return z.getEntityId();
    }

    // Rotates the tree rooted at nodeId to the right and returns the new root
    private int rotateRight(int nodeId) throws IOException {
        EntityNode<T> x = nodeStorage.getEntityNode(nodeId);
        EntityNode<T> z = nodeStorage.getEntityNode(x.getLeftEntityId());
        int newLeftChildX = z.getRightEntityId();
        int newRightChildZ = x.getEntityId();
        int newXHeightDif = 0, newZHeightDif = 0;
        if (z.getHeightDif() == 0) {
            newXHeightDif = -1;
            newZHeightDif = 1;
        }
        nodeStorage.putEntityNode(x.update(newLeftChildX, x.getRightEntityId(), newXHeightDif));
        nodeStorage.putEntityNode(z.update(z.getLeftEntityId(), newRightChildZ, newZHeightDif));
        return z.getEntityId();
    }

    // Rotates the tree rooted at nodeId first to the right then to the left and returns the new root
    private int rotateRightLeft(int nodeId) throws IOException {
        EntityNode<T> x = nodeStorage.getEntityNode(nodeId);
        EntityNode<T> z = nodeStorage.getEntityNode(x.getRightEntityId());
        EntityNode<T> y = nodeStorage.getEntityNode(z.getLeftEntityId());
        int newLeftChildZ = y.getRightEntityId();
        int newRightChildY = z.getEntityId();
        int newRightChildX = y.getLeftEntityId();
        int newLeftChildY = nodeId;
        int newXHeightDif = 0, newYHeightDif = 0, newZHeightDif = 0;
        if (y.getHeightDif() > 0) {
            newXHeightDif = -1;
        } else if (y.getHeightDif() < 0) {
            newZHeightDif = 1;
        }
        nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), newRightChildX, newXHeightDif));
        nodeStorage.putEntityNode(y.update(newLeftChildY, newRightChildY, newYHeightDif));
        nodeStorage.putEntityNode(z.update(newLeftChildZ, z.getRightEntityId(), newZHeightDif));
        return y.getEntityId();
    }

    // Rotates the tree rooted at nodeId first to the left then to the right and returns the new root
    private int rotateLeftRight(int nodeId) throws IOException {
        EntityNode<T> x = nodeStorage.getEntityNode(nodeId);
        EntityNode<T> z = nodeStorage.getEntityNode(x.getLeftEntityId());
        EntityNode<T> y = nodeStorage.getEntityNode(z.getRightEntityId());
        int newRightChildZ = y.getLeftEntityId();
        int newLeftChildY = z.getEntityId();
        int newLeftChildX = y.getRightEntityId();
        int newRightChildY = nodeId;
        int newXHeightDif = 0, newYHeightDif = 0, newZHeightDif = 0;
        if (y.getHeightDif() < 0) {
            newXHeightDif = 1;
        } else if (y.getHeightDif() > 0) {
            newZHeightDif = -1;
        }
        nodeStorage.putEntityNode(x.update(newLeftChildX, x.getRightEntityId(), newXHeightDif));
        nodeStorage.putEntityNode(y.update(newLeftChildY, newRightChildY, newYHeightDif));
        nodeStorage.putEntityNode(z.update(z.getLeftEntityId(), newRightChildZ, newZHeightDif));
        return y.getEntityId();
    }
}
