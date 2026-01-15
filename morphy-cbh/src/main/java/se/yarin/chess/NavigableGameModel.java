package se.yarin.chess;

import org.jetbrains.annotations.NotNull;
import se.yarin.chess.annotations.Annotation;

import java.util.ArrayList;
import java.util.List;

/**
 * A @{@link yarin.chess.GameModel} with a cursor.
 *
 * Contains extra functionality for modifying the game at the current cursor position.
 */
public class NavigableGameModel {
    // The first one should be final so we don't drop listeners
    private final GameModel model;
    private GameMovesModel.Node cursor;
    private List<NavigableGameModelChangeListener> changeListeners = new ArrayList<>();

    public NavigableGameModel() {
        this(new GameModel());
    }

    public NavigableGameModel(@NotNull GameModel model) {
        this.model = model;
        this.cursor = moves().root();
    }

    /**
     * @return the underlying {@link GameMovesModel}
     */
    public GameMovesModel moves() {
        return this.model.moves();
    }

    /**
     * @return the underlying {@link GameHeaderModel}
     */
    public GameHeaderModel header() {
        return this.model.header();
    }

    /**
     * Gets the current position of the cursor.
     * There is no guarantee that the cursor points to a valid node,
     * in case the underlying moves model have been changed.
     * @return the current position of the cursor
     */
    public GameMovesModel.Node cursor() {
        return this.cursor;
    }

    /**
     * Sets the cursor to the specified node, assuming it's a valid node in the game model.
     * @param cursor the new position of the cursor
     * @return true if the cursor was set; false if node was not in the game model
     */
    public boolean setCursor(@NotNull GameMovesModel.Node cursor) {
        if (!cursor.isValid()) {
            return false;
        }
        if (cursor != this.cursor) {
            GameMovesModel.Node oldCursor = this.cursor();
            this.cursor = cursor;
            notifyCursorChanged(oldCursor);
        }
        return true;
    }

    /**
     * Moves the cursor to the previous position.
     * @return true if successful, false otherwise
     */
    public boolean goBack() {
        if (cursor.isRoot()) return false;
        return setCursor(cursor.parent());
    }

    /**
     * Moves the cursor to the next position in the main line.
     * @return true if successful, false otherwise
     */
    public boolean goForward() {
        if (!cursor.hasMoves()) return false;
        return setCursor(cursor.mainNode());
    }

    /**
     * Moves the cursor to the next position in the specified line.
     * Line 0 is the main line, line 1 the first variation etc
     * @return true if successful, false otherwise
     */
    public boolean goForward(int line) {
        if (line < 0 && line >= cursor.numMoves()) return false;
        return setCursor(cursor.children().get(line));
    }

    /**
     * Moves the cursor to the beginning of the game.
     */
    public void goToBeginning() {
        setCursor(moves().root());
    }

    /**
     * Moves the cursor to the end of the current line.
     */
    public void goToEnd() {
        GameMovesModel.Node node = cursor();
        while (node.hasMoves()) {
            node = node.mainNode();
        }
        setCursor(node);
    }

    /**
     * Gets the moves at the current cursor position.
     * @return a list of moves
     */
    public List<Move> getMoves() {
        return cursor.moves();
    }

    /**
     * Adds a move at the current position and updates the cursor.
     * If the position already has moves, a new variation is created.
     * @param move the move to add
     * @throws IllegalMoveException if the move is illegal
     */
    public void addMove(@NotNull Move move) {
        setCursor(cursor().addMove(move));
    }
    
    /**
     * Adds a new main move at the current position and updates the cursor,
     * The old main move becomes a variation (the last one, in case other
     * variations also exist).
     * @param move the move to add
     * @throws IllegalMoveException if the move is illegal
     */
    public void addMainMove(@NotNull Move move) {
        addMove(move);
        promoteVariation();
    }

    /**
     * Overwrites any existing moves at the current position with a new move and updates the cursor.
     * @param move the move to overwrite with
     * @throws IllegalMoveException if the move is illegal
     */
    public void overwriteMove(@NotNull Move move) {
        setCursor(cursor().overwriteMove(move));
    }

    /**
     * Inserts a move at the current position and updates the cursor,
     * see {@link se.yarin.chess.GameMovesModel.Node#insertMove(Move)} )}
     * @param move the move to insert
     * @throws IllegalMoveException if the move is illegal
     */
    public void insertMove(@NotNull Move move) {
        setCursor(cursor().insertMove(move));
    }

    /**
     * Adds an annotation at the current position in the game.
     */
    public void addAnnotation(@NotNull Annotation annotation) {
        cursor().addAnnotation(annotation);
    }

    /**
     * Deletes all annotations at the current position in the game.
     */
    public void deleteAnnotations() {
        cursor().deleteAnnotations();
    }

    /**
     * Deletes all annotations in the game.
     */
    public void deleteAllAnnotations() {
        moves().deleteAllAnnotations();
    }

    /**
     * Deletes the remaining moves in the game.
     */
    public void deleteRemainingMoves() {
        cursor().deleteRemainingMoves();
    }

    /**
     * Deletes the current variation and updates the cursor to the node
     * before the variation started.
     * If the current position is in the main line, nothing happens.
     */
    public void deleteVariation() {
        GameMovesModel.Node node = cursor().deleteVariation();
        if (node != null) {
            setCursor(node);
        }
    }

    /**
     * Promotes the current variation to become the main one.
     */
    public void promoteVariation() {
        cursor().promoteVariation();
    }

    /**
     * Replaces the data in the underlying headers and moves models, including the cursor.
     * @param newModel the model containing the data to replace with
     */
    public void replaceAll(@NotNull NavigableGameModel newModel) {
        // When the game tree is cloned, we need to figure out which new node
        // corresponds to the cursor in newModel.
        // We do this by finding the node index of the cursor node, and then using the fact
        // that the order of the nodes will be same in the cloned version.
        int cursorIndex = newModel.moves().getAllNodes().indexOf(newModel.cursor);
        model.replaceAll(newModel.model);
        if (cursorIndex >= 0) {
            setCursor(model.moves().getAllNodes().get(cursorIndex));
        }
    }

    /**
     * Resets the game model by clearing all the data.
     */
    public void reset() {
        model.reset();
        setCursor(moves().root());
    }

    /**
     * Adds a listener of model changes
     * @param listener the listener
     */
    public void addChangeListener(@NotNull NavigableGameModelChangeListener listener) {
        moves().addChangeListener(listener);
        header().addChangeListener(listener);
        this.changeListeners.add(listener);
    }

    /**
     * Removes a listener of model changes
     * @param listener the listener
     * @return true if the listener was removed
     */
    public boolean removeChangeListener(@NotNull NavigableGameModelChangeListener listener) {
        return this.changeListeners.remove(listener);
    }

    protected void notifyCursorChanged(GameMovesModel.Node oldCursor) {
        for (NavigableGameModelChangeListener changeListener : changeListeners) {
            changeListener.cursorChanged(oldCursor, cursor());
        }
    }
}
