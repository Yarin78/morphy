package se.yarin.morphy.entities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.yarin.morphy.DatabaseContext;
import se.yarin.morphy.TransactionBase;
import se.yarin.morphy.exceptions.MorphyInternalException;

public abstract class EntityIndexTransaction<T extends Entity & Comparable<T>> extends TransactionBase {
    private static final Logger log = LoggerFactory.getLogger(EntityIndexTransaction.class);

    private final @NotNull EntityIndex<T> index;

    protected EntityIndexTransaction(@NotNull DatabaseContext.DatabaseLock lock, @NotNull EntityIndex<T> index) {
        super(lock, index.context());

        this.index = index;
    }

    public @NotNull EntityIndex<T> index() {
        return index;
    }

    protected abstract int version();

    /**
     * Gets an entity by id.
     * @param id the id of the entity
     * @return the entity
     * @throws IllegalArgumentException if there is no entity with the given id
     */
    public @NotNull T get(int id) {
        ensureTransactionIsOpen();
        return deserializeEntity(getNode(id));
    }

    /**
     * Gets an entity by key. If there are multiple entities matching, returns one of them.
     * @param entityKey the key of the entity
     * @return the entity, or null if there was no entity with that key
     */
    public @Nullable T get(@NotNull T entityKey) {
        ensureTransactionIsOpen();
        EntityIndexWriteTransaction<T>.NodePath treePath = lowerBound(entityKey);
        if (treePath.isEnd()) {
            return null;
        }
        T foundEntity = treePath.getEntity();
        if (foundEntity.compareTo(entityKey) == 0) {
            return foundEntity;
        }
        return null;
    }

    protected @NotNull EntityIndexHeader header() {
        return index.storage.getHeader();
    }

    protected @NotNull EntityNode getNode(int id) {
        return index.getNode(id);
    }

    protected @NotNull T deserializeEntity(@NotNull EntityNode node) {
        return index().deserialize(node.getId(), node.getGameCount(), node.getFirstGameId(), node.getSerializedEntity());
    }

    /**
     * Returns a NodePath to the first node which does not compare less than entity, or NodePath.end if no such node exists.
     * If nodes exists with that compares equally to entity, the first of those nodes will be returned.
     */
    public @NotNull NodePath lowerBound(@NotNull T entity) {
        return lowerBound(entity, header().rootNodeId(), null);
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
    public @NotNull NodePath upperBound(@NotNull T entity) {
        return upperBound(entity, header().rootNodeId(), null);
    }

    private @NotNull NodePath upperBound(@NotNull T entity, int currentId, @Nullable NodePath path) {
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

    public @NotNull NodePath begin() {
        return header().rootNodeId() < 0 ? end() : first();
    }

    public @NotNull NodePath end() {
        return new NodePath(-1, null);
    }

    public @NotNull NodePath first() {
        if (header().rootNodeId() < 0) {
            throw new IllegalStateException("There are no nodes in the tree");
        }
        return traverseLeftMost(header().rootNodeId(), null);
    }

    public @NotNull NodePath last() {
        if (header().rootNodeId() < 0) {
            throw new IllegalStateException("There are no nodes in the tree");
        }
        return traverseRightMost(header().rootNodeId(), null);
    }

    private @NotNull NodePath traverseLeftMost(int currentId, @Nullable NodePath path) {
        if (currentId < 0) {
            if (path == null) {
                throw new MorphyInternalException("Internal error traversing Entity index");
            }
            return path;
        }
        return traverseLeftMost(getNode(currentId).getLeftChildId(), new NodePath(currentId, path));
    }

    private @NotNull NodePath traverseRightMost(int currentId, @Nullable NodePath path) {
        if (currentId < 0) {
            if (path == null) {
                throw new MorphyInternalException("Internal error traversing Entity index");
            }
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
        private final @Nullable NodePath parent;

        public void ensureTransactionIsOpen() {
            EntityIndexTransaction.this.ensureTransactionIsOpen();
        }

        protected @Nullable NodePath parent() {
            return parent;
        }

        public int getEntityId() {
            return entityId;
        }

        public NodePath(int entityId, @Nullable NodePath parent) {
            this.entityId = entityId;
            this.parent = parent;
        }

        public boolean isBegin() {
            if (this.entityId < 0 && header().rootNodeId() < 0) {
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
                assert current != null;
            }
            return true;
        }

        public boolean isEnd() {
            return this.entityId < 0;
        }

        public @NotNull EntityNode getNode() {
            if (isEnd()) {
                throw new IllegalStateException("Tried to getNode at end of TreePath");
            }
            // It's important that we fetch the node from the storage as during a transaction
            // the left and right children are changed multiple times, so we shouldn't
            // cache a Node instance in TreePath.
            return EntityIndexTransaction.this.getNode(entityId);
        }

        public @NotNull T getEntity() {
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

        public @NotNull NodePath appendToTail(@NotNull NodePath newTail) {
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

        public @NotNull NodePath successor() {
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
                    assert cur != null;
                }
                successorPath = cur.parent;
                if (successorPath == null) {
                    successorPath = end();
                }
            }
            return successorPath;
        }

        public @NotNull NodePath predecessor() {
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
                        assert cur != null;
                    }
                    predecessorPath = cur.parent;
                }
            }
            assert predecessorPath != null;
            return predecessorPath;
        }

        public @Nullable NodePath trim(int trimEntityId) {
            if (entityId == trimEntityId) {
                return null;
            }
            if (parent == null) {
                throw new MorphyInternalException("Internal error traversing Entity index");
            }
            return new NodePath(entityId, parent.trim(trimEntityId));
        }
    }

}