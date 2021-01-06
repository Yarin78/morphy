package se.yarin.cbhlib.storage;

import lombok.Getter;
import lombok.NonNull;
import se.yarin.cbhlib.entities.Entity;

import java.io.IOException;

/**
 * A path in the Entity node tree. This class is immutable.
 * When using TreePath in iteration, it should never be null; use TreePath.end() to mark end of iteration.
 */
public class TreePath<T extends Entity & Comparable<T>> {
    @Getter private final EntityNodeStorageBase<T> storage;
    @Getter private final int entityId;
    @Getter private final TreePath<T> parent;

    public static <U extends Entity & Comparable<U>> TreePath<U> begin(EntityNodeStorageBase<U> storage) throws IOException {
        if (storage.getRootEntityId() < 0) {
            return end(storage);
        }
        return TreePath.first(storage);
    }

    public static <U extends Entity & Comparable<U>> TreePath<U> end(EntityNodeStorageBase<U> storage) {
        return new TreePath<>(storage, -1, null);
    }

    public TreePath(EntityNodeStorageBase<T> storage, int entityId, TreePath<T> parent) {
        assert parent == null || storage == parent.storage;

        this.storage = storage;
        this.entityId = entityId;
        this.parent = parent;
    }

    public boolean isBegin() throws IOException {
        if (this.entityId < 0 && this.storage.getRootEntityId() < 0) {
            return true;
        }
        if (isEnd() || hasLeftChild()) {
            return false;
        }
        TreePath<T> current = this;
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

    public EntityNode<T> getNode() throws IOException {
        if (isEnd()) {
            throw new IllegalStateException("Tried to getNode at end of TreePath");
        }
        // It's important that we fetch the node from the storage as during a transaction
        // the left and right children are changed multiple times, so we shouldn't
        // cache a Node instance in TreePath.
        return storage.getEntityNode(entityId);
    }

    public T getEntity() throws IOException {
        if (isEnd()) {
            throw new IllegalStateException("Tried to getEntity at end of TreePath");
        }
        return getNode().getEntity();
    }

    public boolean isLeftChild() throws IOException {
        if (isEnd()) {
            throw new IllegalStateException("Tried to check isLeftChild at end of TreePath");
        }
        if (parent == null) {
            return false;
        }
        int parentLeftId = storage.getEntityNode(parent.getEntityId()).getLeftEntityId();
        return entityId == parentLeftId;
    }

    public boolean isRightChild() throws IOException {
        if (isEnd()) {
            throw new IllegalStateException("Tried to check isRightChild at end of TreePath");
        }
        if (parent == null) {
            return false;
        }
        int parentRightId = storage.getEntityNode(parent.getEntityId()).getRightEntityId();
        return entityId == parentRightId;
    }

    public boolean isRoot() {
        if (isEnd()) {
            throw new IllegalStateException("Tried to check isRoot at end of TreePath");
        }
        return parent == null;
    }

    public static <U extends Entity & Comparable<U>> @NonNull TreePath<U> first(EntityNodeStorageBase<U> nodeStorage) throws IOException {
        if (nodeStorage.getRootEntityId() < 0) {
            throw new IllegalStateException("There are no nodes in the tree");
        }
        return traverseLeftMost(nodeStorage, nodeStorage.getRootEntityId(), null);
    }

    public static <U extends Entity & Comparable<U>> @NonNull TreePath<U> last(EntityNodeStorageBase<U> nodeStorage) throws IOException {
        if (nodeStorage.getRootEntityId() < 0) {
            throw new IllegalStateException("There are no nodes in the tree");
        }
        return traverseRightMost(nodeStorage, nodeStorage.getRootEntityId(), null);
    }

    public @NonNull TreePath<T> appendToTail(TreePath<T> newTail) {
        assert storage == newTail.storage;
        return new TreePath<>(storage, entityId, parent == null ? newTail : parent.appendToTail(newTail));
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

    public boolean hasLeftChild() throws IOException {
        if (isEnd()) {
            throw new IllegalStateException("Tried to check left child at end of TreePath");
        }
        return getNode().getLeftEntityId() >= 0;
    }

    public boolean hasRightChild() throws IOException {
        if (isEnd()) {
            throw new IllegalStateException("Tried to check right child at end of TreePath");
        }
        return getNode().getRightEntityId() >= 0;
    }

    public @NonNull TreePath<T> successor() throws IOException {
        if (isEnd()) {
            throw new IllegalStateException("Tried to get successor at end of TreePath");
        }
        int rightEntityId = getNode().getRightEntityId();
        TreePath<T> successorPath;
        if (rightEntityId >= 0) {
            // In ascending traversal, the next node is the leftmost child in the right subtree
            successorPath = traverseLeftMost(storage, rightEntityId, new TreePath<>(storage, entityId, getParent()));
        } else {
            TreePath<T> cur = this;
            while (cur.isRightChild()) {
                cur = cur.getParent();
            }
            successorPath = cur.getParent();
            if (successorPath == null) {
                successorPath = end(storage);
            }
        }
        assert successorPath != null;
        return successorPath;
    }

    public @NonNull TreePath<T> predecessor() throws IOException {
        if (isBegin()) {
            throw new IllegalStateException("Tried to get predecessor at begin of TreePath");
        }
        TreePath<T> predecessorPath;
        if (isEnd()) {
            predecessorPath = TreePath.last(storage);
        } else {
            int leftEntityId = getNode().getLeftEntityId();
            if (leftEntityId >= 0) {
                predecessorPath = traverseRightMost(storage, leftEntityId, new TreePath<>(storage, entityId, getParent()));
            } else {
                TreePath<T> cur = this;
                while (cur.isLeftChild()) {
                    cur = cur.getParent();
                }
                predecessorPath = cur.getParent();
            }
        }
        assert predecessorPath != null;
        return predecessorPath;
    }

    private static <U extends Entity & Comparable<U>> @NonNull TreePath<U> traverseLeftMost(
            EntityNodeStorageBase<U> storage, int currentId, TreePath<U> path) throws IOException {
        if (currentId < 0) {
            return path;
        }
        EntityNode<U> node = storage.getEntityNode(currentId);
        return traverseLeftMost(storage, node.getLeftEntityId(), new TreePath<>(storage, currentId, path));
    }

    private static <U extends Entity & Comparable<U>> @NonNull TreePath<U> traverseRightMost(
            EntityNodeStorageBase<U> storage, int currentId, TreePath<U> path) throws IOException {
        if (currentId < 0) {
            return path;
        }
        EntityNode<U> node = storage.getEntityNode(currentId);
        return traverseRightMost(storage, node.getRightEntityId(), new TreePath<>(storage, currentId, path));
    }

    public TreePath<T> trim(int trimEntityId) {
        if (entityId == trimEntityId) {
            return null;
        }
        return new TreePath<>(storage, entityId, parent.trim(trimEntityId));
    }
}
