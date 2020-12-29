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

        TreePath<T> result = treeSearch(entity);
        if (result != null && result.getCompare() == 0) {
            throw new EntityStorageException("An entity with the same key already exists");
        }

        int entityId;
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

        // Retrace and re-balance the tree
        for(; result != null; result = result.getParent()) {
            // The node in the result path may be old
            EntityNode<T> x = nodeStorage.getEntityNode(result.getNode().getEntityId());
            EntityNode<T> g = result.getParent() != null ? result.getParent().getNode() : null;
            EntityNode<T> n;
            if (z.getEntityId() == x.getRightEntityId()) {
                if (x.getHeightDif() > 0) {
                    n = z.getHeightDif() < 0 ? rotateRightLeft(x, z) : rotateLeft(x, z);
                } else if (x.getHeightDif() < 0) {
                    nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), x.getRightEntityId(), 0));
                    break;
                } else {
                    z = x.update(x.getLeftEntityId(), x.getRightEntityId(), 1);
                    nodeStorage.putEntityNode(z);
                    continue;
                }
            } else {
                if (x.getHeightDif() < 0) {
                    n = z.getHeightDif() > 0 ? rotateLeftRight(x, z) : rotateRight(x, z);
                } else if (x.getHeightDif() > 0) {
                    nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), x.getRightEntityId(), 0));
                    break;
                } else {
                    z = x.update(x.getLeftEntityId(), x.getRightEntityId(), -1);
                    nodeStorage.putEntityNode(z);
                    continue;
                }
            }

            if (g != null) {
                if (x.getEntityId() == g.getLeftEntityId()) {
                    nodeStorage.putEntityNode(g.update(n.getEntityId(), g.getRightEntityId(), g.getHeightDif()));
                } else {
                    nodeStorage.putEntityNode(g.update(g.getLeftEntityId(), n.getEntityId(), g.getHeightDif()));
                }
                break;
            } else {
                nodeStorage.setRootEntityId(n.getEntityId());
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

        // If the node we want to delete has two children, swap it with the in-order successor
        if (node.getLeftEntityId() >= 0 && node.getRightEntityId() >= 0) {
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

            // Ensure we have the whole path from root to the deleted node, so we can retrace
            node = newNode;
            if (successorPath == null) {
                nodePath = new TreePath<>(1, newSuccessorNode, nodePath);
            } else {
                nodePath = successorPath.appendToTail(new TreePath<>(1, newSuccessorNode, nodePath));
            }
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

        // Retrace and re-balance tree
        while (nodePath != null) {
            // The stored value in the path might be old
            EntityNode<T> x = nodeStorage.getEntityNode(nodePath.getNode().getEntityId());
            int comp = nodePath.getCompare();
            nodePath = nodePath.getParent();
            int b;
            EntityNode<T> n;
            if (comp < 0) {
                if (x.getHeightDif() > 0) {
                    EntityNode<T> z = nodeStorage.getEntityNode(x.getRightEntityId());
                    b = z.getHeightDif();
                    n = b < 0 ? rotateRightLeft(x, z) : rotateLeft(x, z);
                } else if (x.getHeightDif() == 0) {
                    nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), x.getRightEntityId(), 1));
                    break;
                } else {
                    nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), x.getRightEntityId(), 0));
                    continue;
                }
            } else {
                if (x.getHeightDif() < 0) {
                    EntityNode<T> z = nodeStorage.getEntityNode(x.getLeftEntityId());
                    b = z.getHeightDif();
                    n = b > 0 ? rotateLeftRight(x, z) : rotateRight(x, z);
                } else if (x.getHeightDif() == 0) {
                    nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), x.getRightEntityId(), -1));
                    break;
                } else {
                    nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), x.getRightEntityId(), 0));
                    continue;
                }
            }


            if (nodePath == null) {
                nodeStorage.setRootEntityId(n.getEntityId());
            } else {
                EntityNode<T> g = nodeStorage.getEntityNode(nodePath.getNode().getEntityId());
                if (x.getEntityId() == g.getLeftEntityId()) {
                    g = g.update(n.getEntityId(), g.getRightEntityId(), g.getHeightDif());
                } else {
                    g = g.update(g.getLeftEntityId(), n.getEntityId(), g.getHeightDif());
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

    // Rotates the tree rooted at x with right child z to the left and returns the new root
    private EntityNode<T> rotateLeft(EntityNode<T> x, EntityNode<T> z) throws IOException {
        nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), z.getLeftEntityId(), z.getHeightDif() == 0 ? 1 : 0));
        nodeStorage.putEntityNode(z.update(x.getEntityId(), z.getRightEntityId(), z.getHeightDif() == 0 ? -1 : 0));
        return z;
    }

    // Rotates the tree rooted at x with the left child z to the right and returns the new root
    private EntityNode<T> rotateRight(EntityNode<T> x, EntityNode<T> z) throws IOException {
        nodeStorage.putEntityNode(x.update(z.getRightEntityId(), x.getRightEntityId(), z.getHeightDif() == 0 ? -1 : 0));
        nodeStorage.putEntityNode(z.update(z.getLeftEntityId(), x.getEntityId(), z.getHeightDif() == 0 ? 1 : 0));
        return z;
    }

    // Rotates the tree rooted at x with right child z first to the right then to the left and returns the new root
    private EntityNode<T> rotateRightLeft(EntityNode<T> x, EntityNode<T> z) throws IOException {
        EntityNode<T> y = nodeStorage.getEntityNode(z.getLeftEntityId());
        nodeStorage.putEntityNode(x.update(x.getLeftEntityId(), y.getLeftEntityId(), y.getHeightDif() > 0 ? -1 : 0));
        nodeStorage.putEntityNode(y.update(x.getEntityId(), z.getEntityId(), 0));
        nodeStorage.putEntityNode(z.update(y.getRightEntityId(), z.getRightEntityId(), y.getHeightDif() < 0 ? 1 : 0));
        return y;
    }

    // Rotates the tree rooted at x with left child z first to the left then to the right and returns the new root
    private EntityNode<T> rotateLeftRight(EntityNode<T> x, EntityNode<T> z) throws IOException {
        EntityNode<T> y = nodeStorage.getEntityNode(z.getRightEntityId());
        nodeStorage.putEntityNode(x.update(y.getRightEntityId(), x.getRightEntityId(), y.getHeightDif() < 0 ? 1 : 0));
        nodeStorage.putEntityNode(y.update(z.getEntityId(), x.getEntityId(), 0));
        nodeStorage.putEntityNode(z.update(z.getLeftEntityId(), y.getLeftEntityId(), y.getHeightDif() > 0 ? -1 : 0));
        return y;
    }
}
