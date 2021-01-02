package se.yarin.cbhlib.entities.storage;

import lombok.Getter;
import se.yarin.cbhlib.entities.Entity;

import java.io.IOException;

/**
 * A path in the Entity node tree. This class is immutable.
 */
public class TreePath<T extends Entity & Comparable<T>> {
    @Getter private EntityNodeStorageBase<T> storage;
    @Getter private EntityNode<T> node; // Store id instead, to avoid stale data?
    @Getter private TreePath<T> parent;

    public TreePath(EntityNodeStorageBase<T> storage, EntityNode<T> node, TreePath<T> parent) {
        assert parent == null || storage == parent.storage;

        this.storage = storage;
        this.node = node;
        this.parent = parent;
    }

    public boolean isLeftChild() throws IOException {
        if (parent == null) {
            return false;
        }
        int parentLeftId = storage.getEntityNode(parent.getNode().getEntityId()).getLeftEntityId();

        return parent != null && node.getEntityId() == parentLeftId;
    }

    public boolean isRightChild() throws IOException {
        if (parent == null) {
            return false;
        }
        int parentRightId = storage.getEntityNode(parent.getNode().getEntityId()).getRightEntityId();

        return parent != null && node.getEntityId() == parentRightId;
    }

    public boolean isRoot() {
        return parent != null;
    }

    public static <U extends Entity & Comparable<U>> TreePath<U> first(EntityNodeStorageBase<U> nodeStorage) throws IOException {
        return traverseLeftMost(nodeStorage, nodeStorage.getRootEntityId(), null);
    }

    public static <U extends Entity & Comparable<U>> TreePath<U> last(EntityNodeStorageBase<U> nodeStorage) throws IOException {
        return traverseRightMost(nodeStorage, nodeStorage.getRootEntityId(), null);
    }

    public TreePath<T> appendToTail(TreePath<T> newTail) {
        assert storage == newTail.storage;
        return new TreePath<T>(storage, node, parent == null ? newTail : parent.appendToTail(newTail));
    }

    @Override
    public String toString() {
        if (parent == null) {
            return "" + node.getEntityId();
        } else {
            return this.node.getEntityId() + " -> " + parent;
        }
    }

    public boolean hasLeftChild() {
        return getNode().getLeftEntityId() >= 0;
    }

    public boolean hasRightChild() {
        return getNode().getRightEntityId() >= 0;
    }

    public TreePath<T> successor() throws IOException {
        int rightEntityId = getNode().getRightEntityId();
        if (rightEntityId >= 0) {
            // In ascending traversal, the next node is the leftmost child in the right subtree
            return traverseLeftMost(storage, rightEntityId, new TreePath<T>(storage, getNode(), getParent()));
        } else {
            TreePath<T> cur = this;
            while (cur.isRightChild()) {
                cur = cur.getParent();
            }
            return cur.getParent();
        }
    }

    public TreePath<T> predecessor() throws IOException {
        int leftEntityId = getNode().getLeftEntityId();
        if (leftEntityId >= 0) {
            return traverseRightMost(storage, leftEntityId, new TreePath<T>(storage, getNode(), getParent()));
        } else {
            TreePath<T> cur = this;
            while (cur.isLeftChild()) {
                cur = cur.getParent();
            }
            return cur.getParent();
        }
    }

    static <U extends Entity & Comparable<U>> TreePath<U> traverseLeftMost(EntityNodeStorageBase<U> storage, int currentId, TreePath<U> path) throws IOException {
        if (currentId < 0) {
            return path;
        }
        EntityNode<U> node = storage.getEntityNode(currentId);
        return traverseLeftMost(storage, node.getLeftEntityId(), new TreePath<U>(storage, node, path));
    }

    static <U extends Entity & Comparable<U>> TreePath<U> traverseRightMost(EntityNodeStorageBase<U> storage, int currentId, TreePath<U> path) throws IOException {
        if (currentId < 0) {
            return path;
        }
        EntityNode<U> node = storage.getEntityNode(currentId);
        return traverseRightMost(storage, node.getRightEntityId(), new TreePath<U>(storage, node, path));
    }

    public TreePath<T> trim(int entityId) {
        if (getNode().getEntityId() == entityId) {
            return null;
        }
        return new TreePath<>(storage, node, parent.trim(entityId));
    }
}
