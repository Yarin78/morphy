package se.yarin.chess;

import lombok.NonNull;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.annotations.Annotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * A model of the chess moves in the game, including variations and annotations.
 * Internally this is represented as a tree of nodes, where each {@link Node} corresponds
 * to a position in the game (or in a variation) and has a {@link Node#lastMove} that was
 * the move the lead to this position.
 *
 * The moves model is mutable and can be inspected or changed using methods such as
 * {@link Node#addMove(Move)}, {@link Node#promoteVariation()}, {@link Node#mainMove()} etc
 *
 * To access the first node in the tree (typically the starting position of the game), use {@link #root()}.
 *
 * Important: A reference to a {@link Node} may become invalid after the model has been changed.
 * Use listeners to detect changes, and {@link Node#isValid()} to determine if a node is still in the tree.
 */
public class GameMovesModel {

    private Node root;
    private List<GameMovesModelChangeListener> changeListeners = new ArrayList<>();

    /**
     * Creates an empty {@link GameMovesModel} with the default chess starting position.
     */
    public GameMovesModel() {
        this.root = new Node(Position.start(), 0);
    }

    /**
     * Creates a {@link GameMovesModel} with a custom starting position.
     * @param startPosition the start position of the game
     * @param moveNumber the current move number
     */
    public GameMovesModel(@NonNull Position startPosition, int moveNumber) {
        if (moveNumber < 1) {
            throw new IllegalArgumentException("Move number must be 1 or greater");
        }
        this.root = new Node(startPosition, Chess.moveNumberToPly(moveNumber, startPosition.playerToMove()));
    }

    /**
     * Creates a duplicate {@link GameMovesModel} by performing a deep clone of the game tree.
     * The listeners are not copied.
     * @param model
     */
    public GameMovesModel(@NonNull GameMovesModel model) {
        this.root = new Node(model.root(), (Node) null);
    }

    /**
     * @return the root node of the game tree
     */
    public Node root() {
        return root;
    }

    /**
     * Gets the number of half moves in the game. This only takes into account moves
     * in the actual stored game tree, not the specified number of moves at the
     * given starting position.
     * @param includeVariations if true, count all moves in all variations;
     *                          otherwise only the main line will be counted
     * @return the total number of half moves in the game
     */
    public int countPly(boolean includeVariations) {
        return root().countPly(includeVariations);
    }

    /**
     * Gets the total number of annotations in the game
     * @return the total number of annotations
     */
    public int countAnnotations() {
        return root().countAnnotations();
    }

    /**
     * @return true if the game tree doesn't start at the beginning of an ordinary chess game.
     * Also returns true for Chess960 games.
     */
    public boolean isSetupPosition() {
        return !(root().position().equals(Position.start()) && root().ply == 0);
    }

    /**
     * Gets a flat list of all nodes in the game in depth first order.
     * The first node returned will be the root node, then the entire main line of the game,
     * then the variations starting at the end, and so on.
     * @return a list of all nodes in the game
     */
    public List<Node> getAllNodes() {
        ArrayList<Node> nodes = new ArrayList<>();
        root().traverseDepthFirst(nodes::add);
        return nodes;
    }

    /**
     * Replaces the game tree with a new tree starting from the specified position
     * @param startPosition the new starting position
     * @param startPly the ply of the starting position
     */
    public void setupPosition(@NonNull Position startPosition, int startPly) {
        if (Chess.isWhitePly(startPly) != (startPosition.playerToMove() == Player.WHITE)) {
            throw new IllegalArgumentException("The player to lastMove according to the ply doesn't match the position");
        }

        this.root = new Node(startPosition, startPly);
        notifyMovesChanged(this.root);
    }

    /**
     * Clears the game tree and initializes a new one from the starting position
     */
    public void reset() {
        setupPosition(Position.start(), 0);
    }

    /**
     * Deletes all annotations in the game.
     */
    public void deleteAllAnnotations() {
        root().traverseDepthFirst(node -> node.internalDeleteAnnotations(true));
        notifyMovesChanged(this.root);
    }

    /**
     * Deletes all variations in the game.
     */
    public void deleteAllVariations() {
        Node current = root();
        while (current != null) {
            if (current.hasVariations()) {
                ArrayList<Node> children = new ArrayList<>(current.children());
                for (int i = 1; i < children.size(); i++) {
                    children.get(i).internalRemoveNode(true);
                }
            }
            current = current.mainNode();
        }
        notifyMovesChanged(this.root);
    }

    /**
     * Replaces the game tree with a copy of the game tree in the specified model.
     * @param moves the moves model to copy the game tree from
     */
    public void replaceAll(@NonNull GameMovesModel moves) {
        this.root = new Node(moves.root, (Node) null);
        notifyMovesChanged(this.root);
    }

    /**
     * Adds a listener of moves model changes
     * @param listener the listener
     */
    public void addChangeListener(@NonNull GameMovesModelChangeListener listener) {
        this.changeListeners.add(listener);
    }

    /**
     * Removes a listener of moves model changes
     * @param listener the listener
     * @return true if the listener was removed
     */
    public boolean removeChangeListener(@NonNull GameMovesModelChangeListener listener) {
        return this.changeListeners.remove(listener);
    }

    protected void notifyMovesChanged(Node node) {
        for (GameMovesModelChangeListener changeListener : changeListeners) {
            changeListener.moveModelChanged(this, node);
        }
    }

    /**
     * Represents a node in the game tree
     */
    public class Node {
        private Node parent;
        // There are typically very few children in each node, so the CopyOnWriteArrayList
        // should be a good thread safe choice
        private List<Node> children = new CopyOnWriteArrayList<>();
        private final Annotations annotations = new Annotations();
        private Move move; // The last move that lead to this node
        private final Position position;
        private final int ply;

        // Developer note: Only methods prefixed with "internal" is allowed
        // to modify the node tree. This is to ensure the integrity of the tree,
        // and that listeners and undo functionality works properly.
        // All internal methods should carry out their actions atomicly.

        private Node(@NonNull Position position, int ply) {
            this.parent = null;
            this.move = null;
            this.position = position;
            this.ply = ply;
        }

        private Node(@NonNull Node parentNode, @NonNull Move move) {
            this.parent = parentNode;
            this.move = move;
            this.position = parentNode.position.doMove(move);
            this.ply = parentNode.ply() + 1;
        }

        /**
         * Performs a deep clone of a node, possibly in another moves model
         * @param sourceNode the node to clone
         * @param newParentNode the parent node the target node should have
         * @return a clone of the sourceNode
         */
        private Node(@NonNull Node sourceNode, Node newParentNode) {
            this.parent = newParentNode;
            this.move = sourceNode.move;
            this.position = sourceNode.position;
            this.ply = sourceNode.ply;
            this.annotations.addAll(sourceNode.annotations);
            this.children = sourceNode.children
                    .stream()
                    .map(child -> new Node(child, this))
                    .collect(Collectors.toList());
        }

        private void validateMove(Move move) {
            if (move.position() != position() || !position.isMoveLegal(move)) {
                throw new IllegalMoveException(position, move);
            }
        }

        // INTERNAL OPERATIONS

        /**
         * Adds a new node to the game tree. Assumes that the move is legal
         * @param move a legal move
         * @param silent if true, don't notify listeners
         * @return the new node
         */
        private Node internalAddNode(Move move, boolean silent) {
            Node node = new Node(this, move);
            this.children.add(node);
            if (!silent) notifyMovesChanged(this);
            return node;
        }

        /**
         * Swaps the order of two of the children in the game tree
         * @param node1 the first child
         * @param node2 the second child
         * @param silent if true, don't notify listeners
         */
        private void internalSwapNodes(Node node1, Node node2, boolean silent) {
            if (node1.parent != this || node2.parent != this) {
                throw new RuntimeException("Can't only swap nodes to which this node is the parent");
            }
            if (node1 == node2) {
                return; // Nothing to do
            }

            children = children
                    .stream()
                    .map(child -> child == node1 ? node2 : child == node2 ? node1 : child)
                    .collect(Collectors.toList());
            if (!silent) notifyMovesChanged(this);
        }

        /**
         * Removes the node itself from the game tree
         * @param silent if true, don't notify listeners
         */
        private void internalRemoveNode(boolean silent) {
            if (isRoot()) {
                throw new RuntimeException("Can't remove the root node");
            }
            parent().children.remove(this);
            if (!silent) notifyMovesChanged(parent());
        }

        /**
         * Removes all children to this node from the game tree
         * @param silent if true, don't notify listeners
         */
        private void internalRemoveAllChildren(boolean silent) {
            children.clear();
            if (!silent) notifyMovesChanged(parent());
        }

        /**
         * Replaces the root node of the game tree with itself
         * @param silent if true, don't notify listeners
         */
        private void internalReplaceRoot(boolean silent) {
            root = this;
            move = null;
            parent = null;
            if (!silent) notifyMovesChanged(this);
        }

        /**
         * Adds an annotation to the node
         * @param annotation the annotation to add
         * @param silent if true, don't notify listeners
         */
        private void internalAddAnnotation(Annotation annotation, boolean silent) {
            this.annotations.add(annotation);
            if (!silent) notifyMovesChanged(this);
        }

        /**
         * Deletes all annotations from the node
         * @param silent if true, don't notify listeners
         */
        private void internalDeleteAnnotations(boolean silent) {
            this.annotations.clear();
            if (!silent) notifyMovesChanged(this);
        }

        // GETTERS

        /**
         * @return true if this is the beginning of the game tree
         */
        public boolean isRoot() {
            return parent == null;
        }

        /**
         * @return true if there is at least one move from this node
         */
        public boolean hasMoves() {
            return children.size() > 0;
        }

        /**
         * @return true if there are more than one move from this node
         */
        public boolean hasVariations() {
            return children.size() > 1;
        }

        /**
         * Gets the number of moves from this node.
         * @return the number of moves from this node
         */
        public int numMoves() {
            return children.size();
        }

        /**
         * Gets the ply number at the current position.
         * The ordinary starting position has ply 0.
         * Note that the root of the game tree may not have ply 0, if a setup position was used.
         * @return the current ply number at this node
         */
        public int ply() {
            return ply;
        }

        /**
         * Gets a read-only list of the children to this node.
         * @return the children of this node
         */
        public List<Node> children() {
            return Collections.unmodifiableList(children);
        }

        /**
         * Gets the parent of this node.
         * @return the parent node
         */
        public Node parent() {
            return parent;
        }

        /**
         * Gets the move that lead to this node in the game tree
         * @return the last move made, or null if this is the root node
         */
        public Move lastMove() {
            return move;
        }

        /**
         * Gets the main move from this position
         * @return the main move, or null if the node has no moves
         */
        public Move mainMove() {
            return hasMoves() ? children.get(0).lastMove() : null;
        }

        /**
         * Gets the first child node from this position, reached when playing the {@link #mainMove()}
         *
         * @return the main node, or null if the node has no child nodes
         */
        public Node mainNode() {
            return hasMoves() ? children.get(0) : null;
        }

        /**
         * Determines if the move leading to this position was the parents main move.
         * @return true if this node is the parents main move
         */
        public boolean isMainMove() {
            if (isRoot()) return true;
            return parent().mainNode() == this;
        }

        /**
         * Determines if this node is part of the main line of the entire game tree.
         * @return true if this node is part of the main line, otherwise false
         */
        public boolean isMainLine() {
            return getVariationDepth() == 0;
        }

        /**
         * Determines if there are no variations from this point forward the game.
         * @return true if there are no variations at this move or any subsequent move
         */
        public boolean isSingleLine() {
            if (!hasMoves()) return true;
            if (hasVariations()) return false;
            return mainNode().isSingleLine();
        }

        /**
         * Gets the depth of this node in the variations. The main line has depth 0,
         * one variation down has depth 1, and so on.
         * @return the depth of the current variation
         */
        public int getVariationDepth() {
            if (isRoot()) return 0;
            return (isMainMove() ? 0 : 1) + this.parent.getVariationDepth();
        }

        /**
         * Returns a list of the moves from this node. The same move may occur multiple times.
         * Changing the contents of this list won't affect the moves model.
         * @return a list of moves from this node
         */
        public List<Move> moves() {
            return children.stream().map(Node::lastMove).collect(Collectors.toList());
        }

        /**
         * Gets the game position at this node.
         * @return the current sgame position
         */
        public Position position() {
            return position;
        }

        /**
         * Determines if this node is still connected to the {@link GameMovesModel}.
         * @return true if the node is valid, otherwise false
         */
        public boolean isValid() {
            if (isRoot()) return root() == this;
            return parent.children.contains(this) && parent.isValid();
        }

        // PUBLIC OPERATIONS

        /**
         * Adds a move to this node.
         * If the node already contains moves (possibly the same move), a new variation is created.
         * @param move the move to add
         * @return the node representing the position after the added move has been made
         * @throws IllegalMoveException if the move is illegal from this position
         */
        public Node addMove(@NonNull Move move) {
            validateMove(move);
            return internalAddNode(move, false);
        }

        /**
         * Adds a move to this node.
         * If the node already contains moves (possibly the same move), a new variation is created.
         * @param move the move to add
         * @return the node representing the position after the added move has been made
         * @throws IllegalMoveException if the move is illegal from this position
         */
        public Node addMove(@NonNull ShortMove move) {
            return addMove(move.toMove(position));
        }

        /**
         * Adds a move to this node.
         * If the node already contains moves (possibly the same move), a new variation is created.
         * @param fromSqi the from square
         * @param toSqi the to square
         * @return the node representing the position after the added move has been made
         * @throws IllegalMoveException if the move is illegal from this position
         */
        public Node addMove(int fromSqi, int toSqi) {
            return addMove(new Move(position, fromSqi, toSqi));
        }

        /**
         * Adds a move to this node.
         * If the node already contains moves (possibly the same move), a new variation is created.
         * @param fromSqi the from square
         * @param toSqi the to square
         * @param promotionStone the new promoted piece
         * @return the node representing the position after the added move has been made
         * @throws IllegalMoveException if the move is illegal from this position
         */
        public Node addMove(int fromSqi, int toSqi, Stone promotionStone) {
            return addMove(new Move(position, fromSqi, toSqi, promotionStone));
        }

        /**
         * Replaces any existing moves from this node and replaces it with a new move.
         * @param move the new move
         * @return the node representing the position after the added move has been made
         * @throws IllegalMoveException if the move is illegal from this position.
         * No change will be made to the game tree in this case.
         */
        public Node overwriteMove(@NonNull Move move) {
            validateMove(move);
            internalRemoveAllChildren(true);
            Node node = internalAddNode(move, true);
            notifyMovesChanged(parent);
            return node;
        }

        /**
         * Replaces any existing moves from this node and replaces it with a new move.
         * @param fromSqi the from square
         * @param toSqi the to square
         * @return the node representing the position after the added move has been made
         * @throws IllegalMoveException if the move is illegal from this position.
         * No change will be made to the game tree in this case.
         */
        public Node overwriteMove(int fromSqi, int toSqi) {
            return overwriteMove(new Move(position, fromSqi, toSqi));
        }

        /**
         * Replaces the existing main move with a new move, and keeps all remaining moves
         * (including variations) in the subtree if they are still legal.
         * If there are multiple moves at this position, the remaining ones will be removed.
         * @param move the move to insert
         * @return the node representing the position after the inserted move has been made
         * @throws IllegalMoveException if the move is illegal from this position.
         * No change will be made to the game tree in this case.
         */
        public Node insertMove(@NonNull Move move) {
            validateMove(move);

            Node oldNode = mainNode();
            internalRemoveAllChildren(true);
            Node newNode = internalAddNode(move, true);
            if (oldNode != null) {
                copyLegalNodes(newNode, oldNode);
            }
            notifyMovesChanged(this);
            return newNode;
        }

        /**
         * Replaces the existing main move with a new move, and keeps all remaining moves
         * (including variations) in the subtree if they are still legal.
         * If there are multiple moves at this position, the remaining ones will be removed.
         * @param fromSqi the from square
         * @param toSqi the to square
         * @return the node representing the position after the inserted move has been made
         * @throws IllegalMoveException if the move is illegal from this position.
         * No change will be made to the game tree in this case.
         */
        public Node insertMove(int fromSqi, int toSqi) {
            return insertMove(new Move(position, fromSqi, toSqi));
        }

        /**
         * Deletes all child nodes to this node
         * @return this node
         */
        public Node deleteRemainingMoves() {
            internalRemoveAllChildren(false);
            notifyMovesChanged(this);
            return this;
        }

        /**
         * Makes this node become the root of the game tree
         * @return this node
         */
        public Node deletePreviousMoves() {
            internalReplaceRoot(false);
            return this;
        }

        /**
         * Makes this node the parents primary child node by replacing it with
         * the parents former primary child node.
         * @return this node
         */
        public Node promoteMove() {
            // Swaps the parents main move with this move
            if (isRoot()) return this;

            parent.internalSwapNodes(this, parent.mainNode(), false);
            return this;
        }

        /**
         * Makes the variation this node is in the primary variation.
         * @return this node
         */
        public Node promoteVariation() {
            if (isRoot()) return this;
            if (isMainMove()) {
                parent.promoteVariation();
            } else {
                promoteMove();
            }
            return this;
        }

        /**
         * Deletes the variation this node is in.
         * If the node is in the main line, nothing happens.
         * @return the parent node of where the variation started,
         * or null if the node was in the main line.
         */
        public Node deleteVariation() {
            if (isRoot()) return null;
            if (isMainMove()) {
                return parent.deleteVariation();
            } else {
                deleteNode();
                return parent;
            }
        }

        /**
         * Deletes this node
         * @return the parent node
         * @throws RuntimeException if the current node is the root
         */
        public Node deleteNode() {
            internalRemoveNode(false);
            return parent();
        }

        /**
         * Converts the game tree from this node downwards to SAN,
         * including variations and annotations.
         *
         * @return the SAN of this node
         */
        public String toSAN() {
            StringBuilder sb = new StringBuilder();
            buildSAN(sb, true);
            return sb.toString();
        }

        /**
         * Adds an annotation to the position.
         * @param annotation the annotation to add
         */
        public Node addAnnotation(Annotation annotation) {
            internalAddAnnotation(annotation, false);
            return this;
        }

        /**
         * Gets all annotations at this position
         * @return a set of annotations
         */
        public Annotations getAnnotations() {
            return annotations;
        }

        /**
         * Gets an annotation of the specified class at this position
         * @param clazz the annotation class to get
         * @return an annotation of the specified class, or null if none existed in this set.
         * If there are multiple annotations with the same class, the first one will be returned.
         */
        public <T extends Annotation> T getAnnotation(Class<T> clazz) {
            return annotations.getByClass(clazz);
        }

        /**
         * Clears all annotations from the position.
         */
        public Node deleteAnnotations() {
            internalDeleteAnnotations(false);
            return this;
        }

        /**
         * Traverse the nodes in the game tree in depth first order
         * @param listener the listener of events when reaching nodes
         */
        public void traverseDepthFirst(@NonNull NodeListener listener) {
            listener.notifyNode(this);
            children.forEach(node -> node.traverseDepthFirst(listener));
        }

        // PRIVATE HELPERS

        private void buildSAN(StringBuilder sb, boolean start) {
            if (hasMoves()) {
                if (!start) sb.append(' ');

                String moveText;
                if (position.playerToMove() == Player.WHITE || start
                        || (isMainMove() && parent.hasVariations())) {
                    moveText = mainMove().toSAN(ply);
                } else {
                    moveText = mainMove().toSAN();
                }
                moveText = mainNode().annotations.format(moveText, true);
                sb.append(moveText);

                if (hasVariations()) {
                    sb.append(" (");
                    for (int i = 1; i < children.size(); i++) {
                        if (i > 1) {
                            sb.append("; ");
                        }
                        Node variation = children.get(i);
                        String varText = variation.lastMove().toSAN(ply);
                        varText = variation.annotations.format(varText, true);
                        sb.append(varText);
                        variation.buildSAN(sb, false);
                    }
                    sb.append(")");
                }
                children.get(0).buildSAN(sb, false);
            }
        }

        private int countPly(boolean includeVariations) {
            int sum = 0;
            for (Node child : children) {
                sum += 1 + child.countPly(includeVariations);
                if (!includeVariations) break;
            }
            return sum;
        }

        public int countAnnotations() {
            int sum = annotations.size();
            for (Node child : children) {
                sum += child.countAnnotations();
            }
            return sum;
        }

        private void copyLegalNodes(Node newNode, Node oldNode) {
            newNode.annotations.addAll(oldNode.annotations);
            for (Node child : oldNode.children()) {
                if (newNode.position.isMoveLegal(child.lastMove())) {
                    Node newChildNode = newNode.internalAddNode(child.lastMove(), true);
                    copyLegalNodes(newChildNode, child);
                }
            }
        }
    }

    @Override
    public String toString() {
        return root().toSAN();
    }
}
