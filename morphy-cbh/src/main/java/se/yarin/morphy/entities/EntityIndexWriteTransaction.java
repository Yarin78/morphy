package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.DatabaseContext;
import se.yarin.morphy.exceptions.MorphyEntityIndexException;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.TreeMap;

public class EntityIndexWriteTransaction<T extends Entity & Comparable<T>> extends EntityIndexTransaction<T> {
    private static final Logger log = LoggerFactory.getLogger(EntityIndexWriteTransaction.class);

    private static final int ENTITY_DELETED = -999;

    // Represents the header within the write transaction
    private @NotNull EntityIndexHeader header;

    // The version of the database the transaction starts from
    private int version;

    // Changes made to the EntityIndex in this transaction
    // Important that they are stored in increasing entity id order
    private final Map<Integer, EntityNode> changes;

    public EntityIndexWriteTransaction(@NotNull EntityIndex<T> index) {
        super(DatabaseContext.DatabaseLock.UPDATE, index);
        this.header = index.storage.getHeader();
        this.version = index.currentVersion();
        this.changes = new TreeMap<>();
    }

    @Override
    protected int version() {
        return version;
    }

    protected @NotNull EntityIndexHeader header() {
        return this.header;
    }

    protected @NotNull EntityNode getNode(int id) {
        // When resolving nodes in the transaction, always first check non-committed changes
        EntityNode node = changes.get(id);
        if (node != null) {
            return node;
        }
        return super.getNode(id);
    }

    /**
     * Looks up an entity based on key and returns the id.
     * If the entity is missing, it's created in the transaction with 0 count in the statistics.
     * @param entity the entity to lookup
     * @return the id of an existing matching entity, or the id of the newly created entity
     */
    public int getOrCreate(@NotNull T entity) {
        T existing = this.get(entity);
        if (existing != null) {
            return existing.id();
        }

        if (entity.count() != 0 || entity.firstGameId() != 0) {
            // New entities should always have 0 in the stats
            // In case entities are added from another database, this might not be the case
            // so they have to be reset before added to this index.
            entity = (T) entity.withCountAndFirstGameId(0, 0);
        }
        return addEntity(entity);
    }

    protected void putNode(EntityNode node) {
        changes.put(node.getId(), node);
    }

    private byte[] serializeEntity(@NotNull T entity) {
        ByteBuffer buf = ByteBuffer.allocate(header.entitySize() - 8);
        index().serialize(entity, buf);
        return buf.array();
    }

    /**
     * Commits the transaction to the database
     */
    public void commit() {
        commit(null);
    }

    /**
     * Checks that the commit is not outdated (already committed or based on a version that's not the current version)
     * @throws IllegalStateException if the commit can't be committed because it's outdated
     */
    public void validateCommit() {
        // Need to check this in case there are two active transactions in the same thread
        if (index().currentVersion() != version()) {
            throw new IllegalStateException(String.format("The database has changed since the transaction started (current version = %d, transaction version = %d)",
                    index().currentVersion(), version()));
        }
    }

    void commit(@Nullable Runnable additionalCommitAction) {
        // Don't attempt to grab the write lock before ensuring that we still have the update lock
        ensureTransactionIsOpen();

        acquireLock(DatabaseContext.DatabaseLock.WRITE);
        try {
            validateCommit();

            for (EntityNode node : changes.values()) {
                index().storage.putItem(node.getId(), node);
            }
            index().storage.putHeader(header);

            if (additionalCommitAction != null) {
                additionalCommitAction.run();
            }

            if (log.isDebugEnabled()) {
                log.debug(String.format("Committed transaction containing %d node changes",
                        changes.values().size()));
            }
            index().bumpVersion();
            clearChanges();
        } finally {
            releaseLock(DatabaseContext.DatabaseLock.WRITE);
        }
    }

    protected void clearChanges() {
        this.header = index().storage.getHeader();
        this.version = index().currentVersion();
        this.changes.clear();
    }

    public void rollback() {
        ensureTransactionIsOpen();
        clearChanges();
    }

    /**
     * Adds a new entity to the storage. The id-field in the entity is ignored.
     * @param entity the entity to add
     * @return the id of the new entity
     */
    public int addEntity(@NotNull T entity) {
        ensureTransactionIsOpen();

        byte[] serializedEntity = serializeEntity(entity);

        // Determine the id of the added entity
        int entityId;
        if (header().deletedEntityId() >= 0) {
            // Replace a deleted entity
            entityId = header().deletedEntityId();
            int newDeletedEntityId = getNode(entityId).getRightChildId();
            header = ImmutableEntityIndexHeader.copyOf(header).withDeletedEntityId(newDeletedEntityId);
        } else {
            // Appended new entity to the end
            entityId = header.capacity();
            header = ImmutableEntityIndexHeader.copyOf(header).withCapacity(entityId + 1);
        }
        header = ImmutableEntityIndexHeader.copyOf(header).withNumEntities(header.numEntities() + 1);

        NodePath path = null;
        if (header.rootNodeId() < 0) {
            // Special case, storage has no root node and is empty
            header = ImmutableEntityIndexHeader.copyOf(header).withRootNodeId(entityId);
        } else {
            path = lowerBound(entity);
            boolean replaceLeft;
            if (path.isEnd()) {
                // The element to add should go last in the tree.
                path = last();
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

            replaceChild(path, path != null && replaceLeft, entityId);
        }

        EntityNode z = new EntityNode(entityId, entity.count(), entity.firstGameId(), serializedEntity);
        putNode(z);

        // Retrace and re-balance the tree
        for(; path != null; path = path.parent()) {
            EntityNode x = path.getNode();
            EntityNode g = path.parent() != null ? path.parent().getNode() : null;
            EntityNode n;
            if (z.getId() == x.getRightChildId()) {
                if (x.getBalance() > 0) {
                    n = z.getBalance() < 0 ? rotateRightLeft(x, z) : rotateLeft(x, z);
                } else if (x.getBalance() < 0) {
                    putNode(x.update(x.getLeftChildId(), x.getRightChildId(), 0));
                    break;
                } else {
                    z = x.update(x.getLeftChildId(), x.getRightChildId(), 1);
                    putNode(z);
                    continue;
                }
            } else {
                if (x.getBalance() < 0) {
                    n = z.getBalance() > 0 ? rotateLeftRight(x, z) : rotateRight(x, z);
                } else if (x.getBalance() > 0) {
                    putNode(x.update(x.getLeftChildId(), x.getRightChildId(), 0));
                    break;
                } else {
                    z = x.update(x.getLeftChildId(), x.getRightChildId(), -1);
                    putNode(z);
                    continue;
                }
            }

            if (g != null) {
                if (x.getId() == g.getLeftChildId()) {
                    putNode(g.update(n.getId(), g.getRightChildId(), g.getBalance()));
                } else {
                    putNode(g.update(g.getLeftChildId(), n.getId(), g.getBalance()));
                }
                break;
            } else {
                header = ImmutableEntityIndexHeader.copyOf(header).withRootNodeId(n.getId());
                break;
            }
        }

        return entityId;
    }

    /**
     * Updates an entity in the storage.
     * @param entityId the entity id to update.
     * @param entity the new entity. {@link Entity#id()} will be ignored.
     */
    public void putEntityById(int entityId, @NotNull T entity) {
        ensureTransactionIsOpen();

        if (entityId < 0 || entityId >= header.capacity()) {
            throw new IllegalArgumentException(String.format("Can't put an entity with id %d when capacity is %d",
                    entityId, header.capacity()));
        }
        EntityNode oldNode = getNode(entityId);
        if (oldNode.isDeleted()) {
            throw new IllegalArgumentException("Can't replace a deleted entity");
        }
        T oldEntity = deserializeEntity(oldNode);
        if (oldEntity.compareTo(entity) == 0) {
            // The key is the same, so we don't have to update the tree
            EntityNode newNode = new EntityNode(entityId, oldNode.getLeftChildId(), oldNode.getRightChildId(),
                    oldNode.getBalance(), entity.count(), entity.firstGameId(), serializeEntity(entity));
            putNode(newNode);
        } else {
            deleteEntity(entityId);
            int newEntityId = addEntity(entity);
            if (entityId != newEntityId) {
                // Important to check this!! Could possibly happen due to race conditions if locking is not
                // implement correctly. Could be a disaster, make sure to not finish such a transaction
                throw new IllegalStateException("Internal error updating an entity; race condition?");
            }
        }
    }

    /**
     * Updates an entity in the storage. The key fields of the entity will
     * determine which entity in the storage to update.
     * @param entity the new entity. {@link Entity#id()} will be ignored.
     * @throws IllegalArgumentException if there is no matching entity, or if there are
     * multiple matching entities
     * @return the id of the entity that was updated
     */
    public int putEntityByKey(@NotNull T entity) {
        ensureTransactionIsOpen();

        NodePath treePath = lowerBound(entity);
        if (treePath.isEnd() || treePath.getEntity().compareTo(entity) != 0) {
            throw new IllegalArgumentException("The entity doesn't exist in the storage");
        }
        NodePath successor = treePath.successor();
        if (!successor.isEnd() && successor.getEntity().compareTo(entity) == 0) {
            throw new IllegalArgumentException("More than one entity matched the key");
        }

        EntityNode node = treePath.getNode();
        EntityNode newNode = new EntityNode(treePath.getEntityId(), node.getLeftChildId(), node.getRightChildId(),  node.getBalance(),
                entity.count(), entity.firstGameId(), serializeEntity(entity));
        putNode(newNode);
        return newNode.getId();
    }

    /**
     * Deletes an entity from the storage.
     * @param entityId the id of the entity to delete
     * @return true if an entity was deleted; false if there was no entity with that id
     */
    public boolean deleteEntity(int entityId) {
        ensureTransactionIsOpen();

        EntityNode node = getNode(entityId);
        if (node.isDeleted()) {
            log.debug("Deleted entity with id " + entityId + " that was already deleted");
            return false;
        }

        // Find the node we want to delete in the tree
        // We need to do this by a tree search since the nodes themselves don't have parent reference
        T entity = deserializeEntity(node);
        NodePath nodePath = lowerBound(entity);

        if (nodePath.isEnd() || nodePath.getEntity().compareTo(entity) != 0) {
            // When doing a search by key, we ended up at a node that didn't match which shouldn't happen
            throw new MorphyEntityIndexException("Broken database structure; couldn't find the node to delete.");
        }

        // In case there are multiples nodes with the same key, we need to identify the correct one
        // by traversing the successors in a linear fashion.
        while (nodePath.getEntityId() != entityId) {
            nodePath = nodePath.successor();
            if (nodePath.isEnd() || nodePath.getEntity().compareTo(entity) != 0) {
                // We found some matching node, but not with the correct id!?
                throw new MorphyEntityIndexException("Broken database structure; couldn't find the node to delete.");
            }
        }

        return internalDeleteEntity(nodePath);
    }

    /**
     * Deletes an entity from the storage.
     * @param entity the entity key to delete
     * @return true if an entity was deleted; false if there was no entity with that key
     * @throws IllegalArgumentException if there are multiple entities with the given key
     */
    public boolean deleteEntity(@NotNull T entity) {
        ensureTransactionIsOpen();

        NodePath nodePath = lowerBound(entity);
        if (nodePath.isEnd() || nodePath.getEntity().compareTo(entity) != 0) {
            log.debug("Deleted entity didn't exist");
            return false;
        }

        NodePath successor = nodePath.successor();
        if (!successor.isEnd() && successor.getEntity().compareTo(entity) == 0) {
            throw new IllegalArgumentException("Multiple matching entities");
        }

        return internalDeleteEntity(nodePath);
    }

    private boolean internalDeleteEntity(@NotNull NodePath deleteEntityPath) {
        EntityNode deleteNode = deleteEntityPath.getNode();
        int deleteEntityId = deleteNode.getId();
        NodePath parentPath = deleteEntityPath.parent();
        boolean deleteNodeIsLeftChild = deleteEntityPath.isLeftChild();

        // If the node we want to delete has two children, swap it with the in-order successor
        if (deleteEntityPath.hasLeftChild() && deleteEntityPath.hasRightChild()) {
            // Find successor node and replace it with this one
            // TODO: This code is a bit ugly, we cut the successor part so it's rooted at the node we want to delete
            // but later on we add the cut out part back. Would be nicer if we could avoid this.
            NodePath successorPath = deleteEntityPath.successor().trim(deleteEntityId);

            EntityNode successorNode = successorPath.getNode();
            // successorPath.node = the node we want to move up and replace node
            boolean successorIsLeft = successorPath.isLeftChild();
            successorPath = successorPath.parent(); // successorPath.node may now equal node!!

            EntityNode newNode = deleteNode.update(successorNode.getLeftChildId(), successorNode.getRightChildId(), successorNode.getBalance());
            int rid = deleteNode.getRightChildId();
            if (rid == successorNode.getId()) {
                rid = deleteNode.getId();
            }
            EntityNode newSuccessorNode = successorNode.update(deleteNode.getLeftChildId(), rid, deleteNode.getBalance());
            replaceChild(parentPath, deleteNodeIsLeftChild, successorNode.getId());
            if (successorPath != null) {
                replaceChild(successorPath, successorIsLeft, deleteNode.getId());
            }
            putNode(newNode);
            putNode(newSuccessorNode);

            // Ensure we have the whole path from root to the deleted node, so we can retrace
            deleteNode = newNode;

            if (successorPath == null) {
                parentPath = new NodePath(newSuccessorNode.getId(), parentPath);
                deleteNodeIsLeftChild = false;
            } else {
                parentPath = successorPath.appendToTail(new NodePath(newSuccessorNode.getId(), parentPath));
                deleteNodeIsLeftChild = successorIsLeft;
            }
        }

        // Now deleteNode has at most one child!
        int childId = deleteNode.getLeftChildId() >= 0 ? deleteNode.getLeftChildId() : deleteNode.getRightChildId();
        replaceChild(parentPath, deleteNodeIsLeftChild, childId);

        // Nothing should now point to the node we want to delete
        EntityNode deletedNode = new EntityNode(deleteEntityId, ENTITY_DELETED, header.deletedEntityId(),
                0, 0, 0, new byte[header.entitySize() - 8]);

        putNode(deletedNode);
        header = ImmutableEntityIndexHeader.copyOf(header)
                .withDeletedEntityId(deleteEntityId)
                .withNumEntities(header.numEntities() - 1);

        // Retrace and re-balance tree
        for(boolean nextIsLeftChild = deleteNodeIsLeftChild;parentPath != null;parentPath = parentPath.parent()) {
            EntityNode x = parentPath.getNode();

            boolean isLeftChild = nextIsLeftChild;
            nextIsLeftChild = parentPath.isLeftChild();

            int b;
            EntityNode n;
            if (isLeftChild) {
                if (x.getBalance() > 0) {
                    EntityNode z = getNode(x.getRightChildId());
                    b = z.getBalance();
                    n = b < 0 ? rotateRightLeft(x, z) : rotateLeft(x, z);
                } else if (x.getBalance() == 0) {
                    putNode(x.update(x.getLeftChildId(), x.getRightChildId(), 1));
                    break;
                } else {
                    putNode(x.update(x.getLeftChildId(), x.getRightChildId(), 0));
                    continue;
                }
            } else {
                if (x.getBalance() < 0) {
                    EntityNode z = getNode(x.getLeftChildId());
                    b = z.getBalance();
                    n = b > 0 ? rotateLeftRight(x, z) : rotateRight(x, z);
                } else if (x.getBalance() == 0) {
                    putNode(x.update(x.getLeftChildId(), x.getRightChildId(), -1));
                    break;
                } else {
                    putNode(x.update(x.getLeftChildId(), x.getRightChildId(), 0));
                    continue;
                }
            }

            NodePath pp = parentPath.parent();
            if (pp == null) {
                header = ImmutableEntityIndexHeader.copyOf(header).withRootNodeId(n.getId());
            } else {
                EntityNode g = pp.getNode();
                if (x.getId() == g.getLeftChildId()) {
                    g = g.update(n.getId(), g.getRightChildId(), g.getBalance());
                } else {
                    g = g.update(g.getLeftChildId(), n.getId(), g.getBalance());
                }
                putNode(g);
                if (b == 0) break;
            }
        }

        return true;
    }

    private void replaceChild(@Nullable NodePath path, boolean replaceLeftChild, int newChildId) {
        if (path == null) {
            // The root node has no parent
            header = ImmutableEntityIndexHeader.copyOf(header).withRootNodeId(newChildId);
        } else {
            EntityNode node = path.getNode();
            if (replaceLeftChild) {
                node = node.update(newChildId, node.getRightChildId(), node.getBalance());
            } else {
                node = node.update(node.getLeftChildId(), newChildId, node.getBalance());
            }
            putNode(node);
        }
    }

    // Rotates the tree rooted at x with right child z to the left and returns the new root
    private @NotNull EntityNode rotateLeft(@NotNull EntityNode x, @NotNull EntityNode z) {
        putNode(x.update(x.getLeftChildId(), z.getLeftChildId(), z.getBalance() == 0 ? 1 : 0));
        putNode(z.update(x.getId(), z.getRightChildId(), z.getBalance() == 0 ? -1 : 0));
        return z;
    }

    // Rotates the tree rooted at x with the left child z to the right and returns the new root
    private @NotNull EntityNode rotateRight(@NotNull EntityNode x, @NotNull EntityNode z) {
        putNode(x.update(z.getRightChildId(), x.getRightChildId(), z.getBalance() == 0 ? -1 : 0));
        putNode(z.update(z.getLeftChildId(), x.getId(), z.getBalance() == 0 ? 1 : 0));
        return z;
    }

    // Rotates the tree rooted at x with right child z first to the right then to the left and returns the new root
    private @NotNull EntityNode rotateRightLeft(@NotNull EntityNode x, @NotNull EntityNode z) {
        EntityNode y = getNode(z.getLeftChildId());
        putNode(x.update(x.getLeftChildId(), y.getLeftChildId(), y.getBalance() > 0 ? -1 : 0));
        putNode(y.update(x.getId(), z.getId(), 0));
        putNode(z.update(y.getRightChildId(), z.getRightChildId(), y.getBalance() < 0 ? 1 : 0));
        return y;
    }

    // Rotates the tree rooted at x with left child z first to the left then to the right and returns the new root
    private @NotNull EntityNode rotateLeftRight(@NotNull EntityNode x, @NotNull EntityNode z) {
        EntityNode y = getNode(z.getRightChildId());
        putNode(x.update(y.getRightChildId(), x.getRightChildId(), y.getBalance() < 0 ? 1 : 0));
        putNode(y.update(z.getId(), x.getId(), 0));
        putNode(z.update(z.getLeftChildId(), y.getLeftChildId(), y.getBalance() > 0 ? -1 : 0));
        return y;
    }
}
