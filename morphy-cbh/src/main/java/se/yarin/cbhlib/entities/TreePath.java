package se.yarin.cbhlib.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.IOException;

/**
 * A path in the Entity node tree. This class is immutable.
 */
@AllArgsConstructor
class TreePath<T extends Entity & Comparable<T>> {
    @Getter private int compare;
    @Getter private EntityNode<T> node;
    @Getter private TreePath<T> parent;

    public TreePath<T> appendToTail(TreePath<T> newTail) {
        return new TreePath<>(compare, node, parent == null ? newTail : parent.appendToTail(newTail));
    }

    /*
    public TreePath<T> successor() {
        T entity = getNode().getEntity();
        int rightEntityId = getNode().getRightEntityId();
        if (rightEntityId >= 0) {
            // In ascending traversal, the next node is the leftmost child in the right subtree
            TreePath<T> treePath = new TreePath<T>(1, getNode(), getParent());
            return traverseLeftMost(rightEntityId, treePath);
        } else {
            TreePath<T> treePath = getParent();
            while (treePath != null && treePath.getCompare() > 0) {
                treePath = treePath.getParent();
            }
            return treePath;
        }
    }

    TreePath<T> traverseLeftMost(int currentId, TreePath<T> path) throws IOException {
        if (currentId < 0) {
            return path;
        }
        EntityNode<T> node = getEntityNode(currentId);
        return traverseLeftMost(node.getLeftEntityId(), new TreePath<>(-1, node, path));
    }
     */
}

