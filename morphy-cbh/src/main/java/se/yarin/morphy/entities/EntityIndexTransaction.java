package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.exceptions.MorphyEntityIndexException;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class EntityIndexTransaction<T extends Entity & Comparable<T>> {
    private static final Logger log = LoggerFactory.getLogger(EntityIndexTransaction.class);

    private static final int ENTITY_DELETED = -999;

    private final EntityIndex<T> index;
    private final int indexNumCommittedTxn;

    // The header reference may be updated
    private EntityIndexHeader header;

    // Changes made to the EntityIndex in this transaction
    // Important that they are stored in increasing entity id order
    private final Map<Integer, EntityNode> changes = new TreeMap<>();

    private boolean committed;

    public EntityIndexTransaction(EntityIndex<T> index) {
        this.index = index;
        this.indexNumCommittedTxn = this.index.getNumCommittedTxn();
        this.header = index.storage.getHeader();
    }

    public EntityIndexHeader header() {
        return header;
    }

    public boolean isCommitted() {
        return committed;
    }

    public T get(int id) {
        return deserializeEntity(getNode(id));
    }

    /**
     * Gets an entity by key. If there are multiple entities matching, returns one of them.
     * @param entityKey the key of the entity
     * @return the entity, or null if there was no entity with that key
     */
    public @Nullable T get(@NotNull T entityKey) {
        EntityIndexTransaction<T>.NodePath treePath = lowerBound(entityKey);
        if (treePath.isEnd()) {
            return null;
        }
        T foundEntity = treePath.getEntity();
        if (foundEntity.compareTo(entityKey) == 0) {
            return foundEntity;
        }
        return null;
    }

    protected EntityNode getNode(int id) {
        // When resolving nodes in the transaction, always first check non-committed changes
        EntityNode node = changes.get(id);
        if (node != null) {
            return node;
        }
        if (this.index.getNumCommittedTxn() != indexNumCommittedTxn) {
            throw new IllegalStateException(String.format("Entity index has changed since transaction started (%d != %d)",
                    this.index.getNumCommittedTxn(), indexNumCommittedTxn));
        }
        // TODO: This needs to be made thread-safe
        node = index.getNode(id);
        return node;
    }

    protected void putNode(EntityNode node) {
        changes.put(node.getId(), node);
    }

    protected T deserializeEntity(EntityNode node) {
        return index.deserialize(node.getId(), node.getGameCount(), node.getFirstGameId(), node.getSerializedEntity());
    }

    private byte[] serializeEntity(T entity) {
        ByteBuffer buf = ByteBuffer.allocate(header.entitySize() - 8);
        index.serialize(entity, buf);
        return buf.array();
    }

    public void commit() {
        if (committed) {
            throw new IllegalStateException("The transaction has already been committed");
        }

        // TODO: Acquire write lock, then verify that version haven't changed, then write

        if (this.index.getNumCommittedTxn() != indexNumCommittedTxn) {
            throw new IllegalStateException("Entity index has changed since transaction started");
        }

        for (EntityNode node : changes.values()) {
            index.storage.putItem(node.getId(), node);
        }
        index.storage.putHeader(header);

        if (log.isDebugEnabled()) {
            log.debug(String.format("Committed transaction containing %d node changes",
                    changes.values().size()));
        }

        committed = true;

        index.transactionCommitted(this);
    }

    /**
     * Returns a NodePath to the first node which does not compare less than entity, or NodePath.end if no such node exists.
     * If nodes exists with that compares equally to entity, the first of those nodes will be returned.
     */
    public @NotNull NodePath lowerBound(@NotNull T entity) {
        return lowerBound(entity, header.rootNodeId(), null);
    }

    private @NotNull NodePath lowerBound(T entity, int currentId, @Nullable NodePath path) {
        if (currentId < 0) {
            return end();
        }

        EntityNode node = getNode(currentId);
        T current = deserializeEntity(node);
        // IMPROVEMENT: Would be nice to be able to compare entities without having to deserialize them
        int comp = entity.compareTo(current);

        path = new NodePath(currentId, path);
        if (comp <= 0) {
            NodePath left = lowerBound(entity, node.getLeftChildId(), path);
            return left.isEnd() ? path : left;
        } else {
            return lowerBound(entity, node.getRightChildId(), path);
        }
    }

    /**
     * Returns a NodePath to the first node which compares greater than entity, or NodePath.end if no such node exists.
     */
    public NodePath upperBound(T entity) {
        return upperBound(entity, header.rootNodeId(), null);
    }

    private NodePath upperBound(T entity, int currentId, NodePath path) {
        if (currentId < 0) {
            return end();
        }

        EntityNode node = getNode(currentId);
        T current = deserializeEntity(node);
        int comp = entity.compareTo(current);

        path = new NodePath(currentId, path);
        if (comp < 0) {
            NodePath left = upperBound(entity, node.getLeftChildId(), path);
            return left.isEnd() ? path : left;
        } else {
            return upperBound(entity, node.getRightChildId(), path);
        }
    }

    public NodePath begin() {
        return header.rootNodeId() < 0 ? end() : first();
    }

    public NodePath end() {
        return new NodePath(-1, null);
    }

    public NodePath first() {
        if (header.rootNodeId() < 0) {
            throw new IllegalStateException("There are no nodes in the tree");
        }
        return traverseLeftMost(header.rootNodeId(), null);
    }

    public NodePath last() {
        if (header.rootNodeId() < 0) {
            throw new IllegalStateException("There are no nodes in the tree");
        }
        return traverseRightMost(header.rootNodeId(), null);
    }

    private NodePath traverseLeftMost(int currentId, NodePath path) {
        if (currentId < 0) {
            return path;
        }
        return traverseLeftMost(getNode(currentId).getLeftChildId(), new NodePath(currentId, path));
    }

    private NodePath traverseRightMost(int currentId, NodePath path) {
        if (currentId < 0) {
            return path;
        }
        return traverseRightMost(getNode(currentId).getRightChildId(), new NodePath(currentId, path));
    }

    /**
     * A path in the Entity node tree. This class is immutable.
     * When using TreePath in iteration, it should never be null; use TreePath.end() to mark end of iteration.
     */
    public class NodePath {
        private final int entityId;
        private final NodePath parent;

        public int getEntityId() {
            return entityId;
        }

        public NodePath(int entityId, NodePath parent) {
            this.entityId = entityId;
            this.parent = parent;
        }

        public boolean isBegin() {
            if (this.entityId < 0 && header.rootNodeId() < 0) {
                return true;
            }
            if (isEnd() || hasLeftChild()) {
                return false;
            }
            NodePath current = this;
            while (!current.isRoot()) {
                if (!current.isLeftChild()) {
                    return false;
                }
                current = current.parent;
            }
            return true;
        }

        public boolean isEnd() {
            return this.entityId < 0;
        }

        public EntityNode getNode() {
            if (isEnd()) {
                throw new IllegalStateException("Tried to getNode at end of TreePath");
            }
            // It's important that we fetch the node from the storage as during a transaction
            // the left and right children are changed multiple times, so we shouldn't
            // cache a Node instance in TreePath.
            return EntityIndexTransaction.this.getNode(entityId);
        }

        public T getEntity() {
            return deserializeEntity(getNode());
        }

        public boolean isLeftChild() {
            if (isEnd()) {
                throw new IllegalStateException("Tried to check isLeftChild at end of TreePath");
            }
            if (parent == null) {
                return false;
            }
            int parentLeftId = EntityIndexTransaction.this.getNode(parent.entityId).getLeftChildId();
            return entityId == parentLeftId;
        }

        public boolean isRightChild() {
            if (isEnd()) {
                throw new IllegalStateException("Tried to check isRightChild at end of TreePath");
            }
            if (parent == null) {
                return false;
            }
            int parentRightId = EntityIndexTransaction.this.getNode(parent.entityId).getRightChildId();
            return entityId == parentRightId;
        }

        public boolean isRoot() {
            if (isEnd()) {
                throw new IllegalStateException("Tried to check isRoot at end of TreePath");
            }
            return parent == null;
        }

        public NodePath appendToTail(NodePath newTail) {
            return new NodePath(entityId, parent == null ? newTail : parent.appendToTail(newTail));
        }

        @Override
        public String toString() {
            if (isEnd()) {
                return "END";
            } else if (parent == null) {
                return "" + entityId;
            } else {
                return entityId + " -> " + parent;
            }
        }

        public boolean hasLeftChild() {
            if (isEnd()) {
                throw new IllegalStateException("Tried to check left child at end of TreePath");
            }
            return getNode().getLeftChildId() >= 0;
        }

        public boolean hasRightChild() {
            if (isEnd()) {
                throw new IllegalStateException("Tried to check right child at end of TreePath");
            }
            return getNode().getRightChildId() >= 0;
        }

        public NodePath successor() {
            if (isEnd()) {
                throw new IllegalStateException("Tried to get successor at end of TreePath");
            }
            int rightEntityId = getNode().getRightChildId();
            NodePath successorPath;
            if (rightEntityId >= 0) {
                // In ascending traversal, the next node is the leftmost child in the right subtree
                successorPath = traverseLeftMost(rightEntityId, new NodePath(entityId, parent));
            } else {
                NodePath cur = this;
                while (cur.isRightChild()) {
                    cur = cur.parent;
                }
                successorPath = cur.parent;
                if (successorPath == null) {
                    successorPath = end();
                }
            }
            assert successorPath != null;
            return successorPath;
        }

        public NodePath predecessor() {
            if (isBegin()) {
                throw new IllegalStateException("Tried to get predecessor at begin of TreePath");
            }
            NodePath predecessorPath;
            if (isEnd()) {
                predecessorPath = last();
            } else {
                int leftEntityId = getNode().getLeftChildId();
                if (leftEntityId >= 0) {
                    predecessorPath = traverseRightMost(leftEntityId, new NodePath(entityId, parent));
                } else {
                    NodePath cur = this;
                    while (cur.isLeftChild()) {
                        cur = cur.parent;
                    }
                    predecessorPath = cur.parent;
                }
            }
            assert predecessorPath != null;
            return predecessorPath;
        }

        public NodePath trim(int trimEntityId) {
            if (entityId == trimEntityId) {
                return null;
            }
            return new NodePath(entityId, parent.trim(trimEntityId));
        }
    }

    /**
     * Adds a new entity to the storage. The id-field in the entity is ignored.
     * @param entity the entity to add
     * @return the id of the new entity
     */
    public int addEntity(T entity) {
        if (committed) {
            throw new IllegalStateException("The transaction has already been committed");
        }

        byte[] serializedEntity = serializeEntity(entity);

        // Determine the id of the added entity
        int entityId;
        if (header.deletedEntityId() >= 0) {
            // Replace a deleted entity
            entityId = header.deletedEntityId();
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
        for(; path != null; path = path.parent) {
            EntityNode x = path.getNode();
            EntityNode g = path.parent != null ? path.parent.getNode() : null;
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
    public void putEntityById(int entityId, T entity) {
        if (committed) {
            throw new IllegalStateException("The transaction has already been committed");
        }

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
    public int putEntityByKey(T entity) {
        if (committed) {
            throw new IllegalStateException("The transaction has already been committed");
        }

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
        if (committed) {
            throw new IllegalStateException("The transaction has already been committed");
        }

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
    public boolean deleteEntity(T entity) {
        if (committed) {
            throw new IllegalStateException("The transaction has already been committed");
        }

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

    private boolean internalDeleteEntity(NodePath deleteEntityPath) {
        EntityNode deleteNode = deleteEntityPath.getNode();
        int deleteEntityId = deleteNode.getId();
        NodePath parentPath = deleteEntityPath.parent;
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
            successorPath = successorPath.parent; // successorPath.node may now equal node!!

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
        for(boolean nextIsLeftChild = deleteNodeIsLeftChild;parentPath != null;parentPath = parentPath.parent) {
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

            NodePath pp = parentPath.parent;
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

    private void replaceChild(NodePath path, boolean replaceLeftChild, int newChildId) {
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
    private EntityNode rotateLeft(EntityNode x, EntityNode z) {
        putNode(x.update(x.getLeftChildId(), z.getLeftChildId(), z.getBalance() == 0 ? 1 : 0));
        putNode(z.update(x.getId(), z.getRightChildId(), z.getBalance() == 0 ? -1 : 0));
        return z;
    }

    // Rotates the tree rooted at x with the left child z to the right and returns the new root
    private EntityNode rotateRight(EntityNode x, EntityNode z) {
        putNode(x.update(z.getRightChildId(), x.getRightChildId(), z.getBalance() == 0 ? -1 : 0));
        putNode(z.update(z.getLeftChildId(), x.getId(), z.getBalance() == 0 ? 1 : 0));
        return z;
    }

    // Rotates the tree rooted at x with right child z first to the right then to the left and returns the new root
    private EntityNode rotateRightLeft(EntityNode x, EntityNode z) {
        EntityNode y = getNode(z.getLeftChildId());
        putNode(x.update(x.getLeftChildId(), y.getLeftChildId(), y.getBalance() > 0 ? -1 : 0));
        putNode(y.update(x.getId(), z.getId(), 0));
        putNode(z.update(y.getRightChildId(), z.getRightChildId(), y.getBalance() < 0 ? 1 : 0));
        return y;
    }

    // Rotates the tree rooted at x with left child z first to the left then to the right and returns the new root
    private EntityNode rotateLeftRight(EntityNode x, EntityNode z) {
        EntityNode y = getNode(z.getRightChildId());
        putNode(x.update(y.getRightChildId(), x.getRightChildId(), y.getBalance() < 0 ? 1 : 0));
        putNode(y.update(z.getId(), x.getId(), 0));
        putNode(z.update(z.getLeftChildId(), y.getLeftChildId(), y.getBalance() > 0 ? -1 : 0));
        return y;
    }

}
