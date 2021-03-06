package se.yarin.morphy.entities;

/**
 * A path in the Entity node tree. This class is immutable.
 * When using TreePath in iteration, it should never be null; use TreePath.end() to mark end of iteration.
 */
public class NodePath<T extends Entity & Comparable<T>> {
    private final EntityIndex<T> index;
    private final int entityId;
    private final NodePath<T> parent;

    public EntityIndex<T> getIndex() {
        return index;
    }

    public int getEntityId() {
        return entityId;
    }

    public static <U extends Entity & Comparable<U>> NodePath<U> begin(EntityIndex<U> index) {
        if (index.storageHeader().rootNodeId() < 0) {
            return end(index);
        }
        return NodePath.first(index);
    }

    public static <U extends Entity & Comparable<U>> NodePath<U> end(EntityIndex<U> index) {
        return new NodePath<>(index, -1, null);
    }

    public NodePath(EntityIndex<T> index, int entityId, NodePath<T> parent) {
        assert parent == null || index == parent.index;

        this.index = index;
        this.entityId = entityId;
        this.parent = parent;
    }

    public boolean isBegin() {
        if (this.entityId < 0 && index.storageHeader().rootNodeId() < 0) {
            return true;
        }
        if (isEnd() || hasLeftChild()) {
            return false;
        }
        NodePath<T> current = this;
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
        return index.getNode(entityId);
    }

    public T getEntity() {
        if (isEnd()) {
            throw new IllegalStateException("Tried to getEntity at end of TreePath");
        }
        return index.get(entityId);
    }

    public boolean isLeftChild() {
        if (isEnd()) {
            throw new IllegalStateException("Tried to check isLeftChild at end of TreePath");
        }
        if (parent == null) {
            return false;
        }
        int parentLeftId = index.getNode(parent.entityId).getLeftChildId();
        return entityId == parentLeftId;
    }

    public boolean isRightChild() {
        if (isEnd()) {
            throw new IllegalStateException("Tried to check isRightChild at end of TreePath");
        }
        if (parent == null) {
            return false;
        }
        int parentRightId = index.getNode(parent.entityId).getRightChildId();
        return entityId == parentRightId;
    }

    public boolean isRoot() {
        if (isEnd()) {
            throw new IllegalStateException("Tried to check isRoot at end of TreePath");
        }
        return parent == null;
    }

    public static <U extends Entity & Comparable<U>> NodePath<U> first(EntityIndex<U> index) {
        if (index.storageHeader().rootNodeId() < 0) {
            throw new IllegalStateException("There are no nodes in the tree");
        }
        return traverseLeftMost(index, index.storageHeader().rootNodeId(), null);
    }

    public static <U extends Entity & Comparable<U>> NodePath<U> last(EntityIndex<U> index) {
        if (index.storageHeader().rootNodeId() < 0) {
            throw new IllegalStateException("There are no nodes in the tree");
        }
        return traverseRightMost(index, index.storageHeader().rootNodeId(), null);
    }

    public NodePath<T> appendToTail(NodePath<T> newTail) {
        assert index == newTail.index;
        return new NodePath<>(index, entityId, parent == null ? newTail : parent.appendToTail(newTail));
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

    public NodePath<T> successor() {
        if (isEnd()) {
            throw new IllegalStateException("Tried to get successor at end of TreePath");
        }
        int rightEntityId = getNode().getRightChildId();
        NodePath<T> successorPath;
        if (rightEntityId >= 0) {
            // In ascending traversal, the next node is the leftmost child in the right subtree
            successorPath = traverseLeftMost(index, rightEntityId, new NodePath<>(index, entityId, parent));
        } else {
            NodePath<T> cur = this;
            while (cur.isRightChild()) {
                cur = cur.parent;
            }
            successorPath = cur.parent;
            if (successorPath == null) {
                successorPath = end(index);
            }
        }
        assert successorPath != null;
        return successorPath;
    }

    public NodePath<T> predecessor() {
        if (isBegin()) {
            throw new IllegalStateException("Tried to get predecessor at begin of TreePath");
        }
        NodePath<T> predecessorPath;
        if (isEnd()) {
            predecessorPath = NodePath.last(index);
        } else {
            int leftEntityId = getNode().getLeftChildId();
            if (leftEntityId >= 0) {
                predecessorPath = traverseRightMost(index, leftEntityId, new NodePath<>(index, entityId, parent));
            } else {
                NodePath<T> cur = this;
                while (cur.isLeftChild()) {
                    cur = cur.parent;
                }
                predecessorPath = cur.parent;
            }
        }
        assert predecessorPath != null;
        return predecessorPath;
    }

    private static <U extends Entity & Comparable<U>> NodePath<U> traverseLeftMost(
            EntityIndex<U> storage, int currentId, NodePath<U> path) {
        if (currentId < 0) {
            return path;
        }
        EntityNode node = storage.getNode(currentId);
        return traverseLeftMost(storage, node.getLeftChildId(), new NodePath<>(storage, currentId, path));
    }

    private static <U extends Entity & Comparable<U>> NodePath<U> traverseRightMost(
            EntityIndex<U> storage, int currentId, NodePath<U> path) {
        if (currentId < 0) {
            return path;
        }
        EntityNode node = storage.getNode(currentId);
        return traverseRightMost(storage, node.getRightChildId(), new NodePath<>(storage, currentId, path));
    }

    public NodePath<T> trim(int trimEntityId) {
        if (entityId == trimEntityId) {
            return null;
        }
        return new NodePath<>(index, entityId, parent.trim(trimEntityId));
    }
}