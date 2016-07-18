package se.yarin.cbhlib.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;

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
}
