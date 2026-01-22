package se.yarin.chess.pgn;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.*;
import se.yarin.chess.annotations.Annotation;
import se.yarin.chess.annotations.CommentaryAfterMoveAnnotation;
import se.yarin.chess.annotations.CommentaryBeforeMoveAnnotation;
import se.yarin.chess.annotations.NAGAnnotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Builder for constructing a GameMovesModel tree from PGN move text.
 * Handles variations and annotations.
 */
public class PgnGameBuilder {

    private final GameMovesModel movesModel;
    private GameMovesModel.Node currentNode;
    private Position currentPosition;

    // Stack for handling variations
    private final Stack<GameMovesModel.Node> variationStack = new Stack<>();
    private final Stack<Position> positionStack = new Stack<>();

    // Pending annotations to add to the next node (or current node for after-move comments)
    private final List<Annotation> pendingAnnotations = new ArrayList<>();

    // Track if we just started a variation (no moves added yet in current variation)
    private boolean isVariationStart = false;

    /**
     * Creates a builder with the standard starting position.
     */
    public PgnGameBuilder() {
        this(new GameMovesModel());
    }

    /**
     * Creates a builder with a custom starting position.
     *
     * @param movesModel the moves model to build into
     */
    public PgnGameBuilder(@NotNull GameMovesModel movesModel) {
        this.movesModel = movesModel;
        this.currentNode = movesModel.root();
        this.currentPosition = movesModel.root().position();
    }

    /**
     * Adds a move to the current position.
     *
     * @param move the move to add
     * @return the builder for chaining
     */
    @NotNull
    public PgnGameBuilder addMove(@NotNull Move move) {
        // Add the move as a child of the current node
        GameMovesModel.Node newNode = currentNode.addMove(move);

        // Add pending annotations to the new node
        for (Annotation annotation : pendingAnnotations) {
            newNode.getAnnotations().add(annotation);
        }
        pendingAnnotations.clear();

        // Update current position and node
        currentNode = newNode;
        currentPosition = currentPosition.doMove(move);

        // We've added a move, so we're no longer at the start of a variation
        isVariationStart = false;

        return this;
    }

    /**
     * Starts a new variation from the current node's parent.
     * The variation will start from the parent's position.
     */
    public void startVariation() {
        if (currentNode.isRoot()) {
            throw new IllegalStateException("Cannot start variation from root");
        }

        // Push current state onto stack
        variationStack.push(currentNode);
        positionStack.push(currentPosition);

        // Rewind to parent node for the variation
        currentNode = currentNode.parent();
        currentPosition = currentNode.position();

        // Clear pending annotations
        pendingAnnotations.clear();

        // Mark that we just started a variation (no moves added yet)
        isVariationStart = true;
    }

    /**
     * Ends the current variation and returns to the previous context.
     */
    public void endVariation() {
        if (variationStack.isEmpty()) {
            throw new IllegalStateException("No variation to end");
        }

        // Restore previous state from stack
        currentNode = variationStack.pop();
        currentPosition = positionStack.pop();

        // Clear pending annotations
        pendingAnnotations.clear();

        // We're back in the previous context, not at the start of a variation
        isVariationStart = false;
    }

    /**
     * Adds a NAG annotation to the current move (NAGs appear after the move in PGN).
     *
     * @param nag the NAG to add
     * @return the builder for chaining
     */
    @NotNull
    public PgnGameBuilder addNAG(@NotNull NAG nag) {
        if (currentNode.isRoot()) {
            // If we're at root, save it for the next move
            pendingAnnotations.add(new NAGAnnotation(nag));
        } else {
            // Add to the current move
            currentNode.getAnnotations().add(new NAGAnnotation(nag));
        }
        return this;
    }

    /**
     * Adds a commentary annotation before the next move.
     *
     * @param comment the comment text
     * @return the builder for chaining
     */
    @NotNull
    public PgnGameBuilder addCommentBefore(@NotNull String comment) {
        if (!comment.trim().isEmpty()) {
            pendingAnnotations.add(new CommentaryBeforeMoveAnnotation(comment));
        }
        return this;
    }

    /**
     * Adds a commentary annotation after the current move.
     *
     * @param comment the comment text
     * @return the builder for chaining
     */
    @NotNull
    public PgnGameBuilder addCommentAfter(@NotNull String comment) {
        if (!comment.trim().isEmpty()) {
            if (currentNode.isRoot()) {
                // If we're at the root, treat it as a before-move comment for the first move
                pendingAnnotations.add(new CommentaryBeforeMoveAnnotation(comment));
            } else {
                currentNode.getAnnotations().add(new CommentaryAfterMoveAnnotation(comment));
            }
        }
        return this;
    }

    /**
     * Gets the current position in the game.
     *
     * @return the current position
     */
    @NotNull
    public Position getCurrentPosition() {
        return currentPosition;
    }

    /**
     * Checks if we're at a position where comments should be treated as "before" comments.
     * This is true if we're at the root or if we just started a variation (no moves added yet).
     *
     * @return true if at a position for before-move comments
     */
    public boolean isAtBeforeCommentPosition() {
        return currentNode.isRoot() || isVariationStart;
    }

    /**
     * Gets the current node in the game tree.
     *
     * @return the current node
     */
    @NotNull
    public GameMovesModel.Node getCurrentNode() {
        return currentNode;
    }

    /**
     * Gets the constructed moves model.
     *
     * @return the game moves model
     */
    @NotNull
    public GameMovesModel getMovesModel() {
        return movesModel;
    }
}
