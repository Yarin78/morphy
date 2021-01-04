package se.yarin.cbhlib.storage.transaction;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.cbhlib.entities.Entity;
import se.yarin.cbhlib.storage.EntityStorageDuplicateKeyException;
import se.yarin.cbhlib.storage.EntityStorageException;
import se.yarin.cbhlib.storage.EntityNode;
import se.yarin.cbhlib.storage.EntityNodeStorageBase;
import se.yarin.cbhlib.storage.TreePath;

import java.io.IOException;

public class EntityStorageTransaction<T extends Entity & Comparable<T>> {
    private static final Logger log = LoggerFactory.getLogger(EntityStorageTransaction.class);

    private static final int ENTITY_DELETED = -999;

    private final TransactionalNodeStorage<T> nodeStorage;
    private final EntityNodeStorageBase<T> underlyingNodeStorage;
    private final EntityStorage<T> storage;
    private boolean committed = false;

    public EntityStorageTransaction(@NonNull EntityStorage<T> storage,
                             @NonNull EntityNodeStorageBase<T> nodeStorage) {
        this.storage = storage;
        this.underlyingNodeStorage = nodeStorage;
        this.nodeStorage = new TransactionalNodeStorage<>(nodeStorage);
    }

    public synchronized void commit() throws EntityStorageException, IOException {
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

    private TreePath<T> lowerBound(@NonNull T entity) throws IOException {
        return nodeStorage.lowerBound(entity);
    }

    private TreePath<T> upperBound(@NonNull T entity) throws IOException {
        return nodeStorage.upperBound(entity);
    }

    public T getEntity(int entityId) throws IOException {
        if (entityId < 0 || entityId >= nodeStorage.getCapacity()) {
            return null;
        }
        return nodeStorage.getEntityNode(entityId).getEntity();
    }

    public T getAnyEntity(@NonNull T entity) throws IOException {
        TreePath<T> treePath = lowerBound(entity);
        if (treePath == null) {
            return null;
        }
        T foundEntity = treePath.getNode().getEntity();
        if (foundEntity.compareTo(entity) == 0) {
            return foundEntity;
        }
        return null;
    }

    public int getNumEntities() {
        return nodeStorage.getNumEntities();
    }

    public int getCapacity() {
        return nodeStorage.getCapacity();
    }


    /**
     * Adds a new entity to the storage. The id-field in the entity is ignored.
     * @param entity the entity to add
     * @return the id of the new entity
     */
    public int addEntity(@NonNull T entity) throws IOException {
        if (committed) {
            throw new IllegalStateException("The transaction has already been committed");
        }

        TreePath<T> path = lowerBound(entity);
        boolean replaceLeft;
        if (path == null) {
            // The element to add should go last in the tree.
            path = TreePath.last(nodeStorage);
            replaceLeft = false;
        } else {
            // Insert to the left of the lower bound.
            // If we already have a left child, insert it to the right of the predecessor instead.
            if (!path.hasLeftChild()) {
                replaceLeft = true;
            } else {
                path = path.predecessor();
                replaceLeft = false;
            }
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

        replaceChild(path, path != null && replaceLeft, entityId);

        EntityNode<T> z = nodeStorage.createNode(entityId, entity);
        nodeStorage.putEntityNode(z);

        // Retrace and re-balance the tree
        for(; path != null; path = path.getParent()) {
            EntityNode<T> x = path.getNode();
            EntityNode<T> g = path.getParent() != null ? path.getParent().getNode() : null;
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
     */
    public void putEntityById(int entityId, @NonNull T entity) throws EntityStorageException, IOException {
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
     * @throws EntityStorageDuplicateKeyException if more than one existing key exists
     */
    public void putEntityByKey(@NonNull T entity) throws EntityStorageException, IOException {
        if (committed) {
            throw new IllegalStateException("The transaction has already been committed");
        }

        TreePath<T> treePath = lowerBound(entity);
        if (treePath == null || treePath.getEntity().compareTo(entity) != 0) {
            throw new EntityStorageException("The entity doesn't exist in the storage");
        }
        TreePath<T> successor = treePath.successor();
        if (successor.getEntity().compareTo(entity) == 0) {
            throw new EntityStorageDuplicateKeyException("More than one entity matched the key");
        }

        EntityNode<T> node = treePath.getNode();
        EntityNode<T> newNode = nodeStorage.createNode(treePath.getEntityId(), entity);
        newNode = newNode.update(node.getLeftEntityId(), node.getRightEntityId(), node.getHeightDif());
        nodeStorage.putEntityNode(newNode);
    }

    /**
     * Deletes an entity from the storage.
     * @param entityId the id of the entity to delete
     * @return true if an entity was deleted; false if there was no entity with that id
     */
    public boolean deleteEntity(int entityId) throws IOException, EntityStorageException {
        if (committed) {
            throw new IllegalStateException("The transaction has already been committed");
        }

        EntityNode<T> node = nodeStorage.getEntityNode(entityId);
        if (node.isDeleted()) {
            log.debug("Deleted entity with id " + entityId + " that was already deleted");
            return false;
        }

        // Find the node we want to delete in the tree
        // We need to do this by a tree search since the nodes themselves don't have parent reference
        TreePath<T> nodePath = lowerBound(node.getEntity());

        if (nodePath == null || nodePath.getEntity().compareTo(node.getEntity()) != 0) {
            // When doing a search by key, we ended up at a node that didn't match which shouldn't happen
            throw new EntityStorageException("Broken database structure; couldn't find the node to delete.");
        }

        // In case there are multiples nodes with the same key, we need to identify the correct one
        // by traversing the successors in a linear fashion.
        while (nodePath.getEntityId() != entityId) {
            nodePath = nodePath.successor();
            if (nodePath == null || nodePath.getEntity().compareTo(node.getEntity()) != 0) {
                // We found some matching node, but not with the correct id!?
                throw new EntityStorageException("Broken database structure; couldn't find the node to delete.");
            }
        }

        return internalDeleteEntity(nodePath);
    }

    /**
     * Deletes an entity from the storage.
     * @param entity the entity key to delete
     * @return true if an entity was deleted; false if there was no entity with that key
     * @throws EntityStorageDuplicateKeyException if there are multiple entities with the given key
     */
    public boolean deleteEntity(T entity) throws IOException, EntityStorageDuplicateKeyException {
        if (committed) {
            throw new IllegalStateException("The transaction has already been committed");
        }

        TreePath<T> nodePath = lowerBound(entity);
        if (nodePath == null || nodePath.getEntity().compareTo(entity) != 0) {
            log.debug("Deleted entity didn't exist");
            return false;
        }

        TreePath<T> successor = nodePath.successor();
        if (successor != null && successor.getEntity().compareTo(entity) == 0) {
            throw new EntityStorageDuplicateKeyException("Multiple matching entities");
        }
        return internalDeleteEntity(nodePath);
    }


    private boolean internalDeleteEntity(TreePath<T> deleteEntityPath) throws IOException {
        assert this.nodeStorage == deleteEntityPath.getStorage();

        EntityNode<T> deleteNode = deleteEntityPath.getNode();
        int deleteEntityId = deleteNode.getEntityId();
        TreePath<T> parentPath = deleteEntityPath.getParent();
        boolean deleteNodeIsLeftChild = deleteEntityPath.isLeftChild();

        // If the node we want to delete has two children, swap it with the in-order successor
        if (deleteEntityPath.hasLeftChild() && deleteEntityPath.hasRightChild()) {
            // Find successor node and replace it with this one
            // TODO: This code is a bit ugly, we cut the successor part so it's rooted at the node we want to delete
            // but later on we add the cut out part back. Would be nicer if we could avoid this.
            TreePath<T> successorPath = deleteEntityPath.successor().trim(deleteEntityId);

            EntityNode<T> successorNode = successorPath.getNode();
            // successorPath.node = the node we want to move up and replace node
            boolean successorIsLeft = successorPath.isLeftChild();
            successorPath = successorPath.getParent(); // successorPath.node may now equal node!!

            EntityNode<T> newNode = deleteNode.update(successorNode.getLeftEntityId(), successorNode.getRightEntityId(), successorNode.getHeightDif());
            int rid = deleteNode.getRightEntityId();
            if (rid == successorNode.getEntityId()) {
                rid = deleteNode.getEntityId();
            }
            EntityNode<T> newSuccessorNode = successorNode.update(deleteNode.getLeftEntityId(), rid, deleteNode.getHeightDif());
            replaceChild(parentPath, deleteNodeIsLeftChild, successorNode.getEntityId());
            if (successorPath != null) {
                replaceChild(successorPath, successorIsLeft, deleteNode.getEntityId());
            }
            nodeStorage.putEntityNode(newNode);
            nodeStorage.putEntityNode(newSuccessorNode);

            // Ensure we have the whole path from root to the deleted node, so we can retrace
            deleteNode = newNode;

            if (successorPath == null) {
                parentPath = new TreePath<>(this.nodeStorage, newSuccessorNode.getEntityId(), parentPath);
                deleteNodeIsLeftChild = false;
            } else {
                parentPath = successorPath.appendToTail(new TreePath<>(this.nodeStorage, newSuccessorNode.getEntityId(), parentPath));
                deleteNodeIsLeftChild = successorIsLeft;
            }
        }

        // Now deleteNode has at most one child!
        int childId = deleteNode.getLeftEntityId() >= 0 ? deleteNode.getLeftEntityId() : deleteNode.getRightEntityId();
        replaceChild(parentPath, deleteNodeIsLeftChild, childId);

        // Nothing should now point to the node we want to delete
        EntityNode<T> deletedNode = nodeStorage.createNode(deleteEntityId, null)
                .update(ENTITY_DELETED, nodeStorage.getFirstDeletedEntityId(), 0);

        nodeStorage.putEntityNode(deletedNode);
        nodeStorage.setFirstDeletedEntityId(deleteEntityId);
        nodeStorage.setNumEntities(nodeStorage.getNumEntities() - 1);

        // Retrace and re-balance tree
        for(boolean nextIsLeftChild = deleteNodeIsLeftChild;parentPath != null;parentPath = parentPath.getParent()) {
            EntityNode<T> x = parentPath.getNode();

            boolean isLeftChild = nextIsLeftChild;
            nextIsLeftChild = parentPath.isLeftChild();

            int b;
            EntityNode<T> n;
            if (isLeftChild) {
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

            TreePath<T> pp = parentPath.getParent();
            if (pp == null) {
                nodeStorage.setRootEntityId(n.getEntityId());
            } else {
                EntityNode<T> g = pp.getNode();
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

    private void replaceChild(TreePath<T> path, boolean replaceLeftChild, int newChildId) throws IOException {
        if (path == null) {
            // The root node has no parent
            nodeStorage.setRootEntityId(newChildId);
        } else {
            EntityNode<T> node = path.getNode();
            if (replaceLeftChild) {
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
