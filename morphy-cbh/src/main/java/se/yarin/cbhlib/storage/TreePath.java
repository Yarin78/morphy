package se.yarin.cbhlib.storage;

import lombok.Getter;
import se.yarin.cbhlib.entities.Entity;

import java.io.IOException;

/**
 * A path in the Entity node tree. This class is immutable.
 */
public class TreePath<T extends Entity & Comparable<T>> {
    @Getter private EntityNodeStorageBase<T> storage;
    @Getter private int entityId;
    @Getter private TreePath<T> parent;

    public TreePath(EntityNodeStorageBase<T> storage, int entityId, TreePath<T> parent) {
        assert parent == null || storage == parent.storage;

        this.storage = storage;
        this.entityId = entityId;
        this.parent = parent;
    }

    public EntityNode<T> getNode() throws IOException {
        // It's important that we fetch the node from the storage as during a transaction
        // the left and right children are changed multiple times, so we shouldn't
        // cache a Node instance in TreePath.
        return storage.getEntityNode(entityId);
    }

    public T getEntity() throws IOException {
        return getNode().getEntity();
    }

    public boolean isLeftChild() throws IOException {
        if (parent == null) {
            return false;
        }
        int parentLeftId = storage.getEntityNode(parent.getEntityId()).getLeftEntityId();
        return parent != null && entityId == parentLeftId;
    }

    public boolean isRightChild() throws IOException {
        if (parent == null) {
            return false;
        }
        int parentRightId = storage.getEntityNode(parent.getEntityId()).getRightEntityId();
        return parent != null && entityId == parentRightId;
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
        return new TreePath<T>(storage, entityId, parent == null ? newTail : parent.appendToTail(newTail));
    }

    @Override
    public String toString() {
        if (parent == null) {
            return "" + entityId;
        } else {
            return entityId + " -> " + parent;
        }
    }

    public boolean hasLeftChild() throws IOException {
        return getNode().getLeftEntityId() >= 0;
    }

    public boolean hasRightChild() throws IOException {
        return getNode().getRightEntityId() >= 0;
    }

    public TreePath<T> successor() throws IOException {
        int rightEntityId = getNode().getRightEntityId();
        if (rightEntityId >= 0) {
            // In ascending traversal, the next node is the leftmost child in the right subtree
            return traverseLeftMost(storage, rightEntityId, new TreePath<>(storage, entityId, getParent()));
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
            return traverseRightMost(storage, leftEntityId, new TreePath<>(storage, entityId, getParent()));
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
        return traverseLeftMost(storage, node.getLeftEntityId(), new TreePath<U>(storage, currentId, path));
    }

    static <U extends Entity & Comparable<U>> TreePath<U> traverseRightMost(EntityNodeStorageBase<U> storage, int currentId, TreePath<U> path) throws IOException {
        if (currentId < 0) {
            return path;
        }
        EntityNode<U> node = storage.getEntityNode(currentId);
        return traverseRightMost(storage, node.getRightEntityId(), new TreePath<U>(storage, currentId, path));
    }

    public TreePath<T> trim(int trimEntityId) {
        if (entityId == trimEntityId) {
            return null;
        }
        return new TreePath<>(storage, entityId, parent.trim(trimEntityId));
    }
}
