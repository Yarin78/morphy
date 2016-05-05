package yarin.chess;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * yarin.chess.GamePosition represents a position in a recorded chess game or line.
 * It contains a list of moves (possibly 0) that moves the game forward.
 * The first move in this list is the main variation.
 */
public class GamePosition {
    private Game ownerGame;
    private LinkedHashMap<Move, GamePosition> moveMap; // First move is main variation

    private Move lastMove;
    private GamePosition previousPosition; // null if start position
    private Board board; // The board position;
    private int moveNo;

    protected GamePosition(GamePosition previousPosition, Move move) {
        ownerGame = previousPosition.getOwnerGame();
        this.previousPosition = previousPosition;
        lastMove = move;
        board = previousPosition.getPosition().doMove(move);
        moveNo = previousPosition.getMoveNumber();
        if (board.getToMove() == Piece.PieceColor.WHITE)
            moveNo++;
        moveMap = new LinkedHashMap<>();
    }

    protected GamePosition(Board board, int moveNo) {
        if (!(this instanceof Game))
            throw new IllegalArgumentException("This constructor should only be used by yarin.chess.Game");
        if (board == null)
            throw new IllegalArgumentException("board must not be null");

        ownerGame = (Game) this;
        previousPosition = null;
        lastMove = null;
        this.moveNo = moveNo;
        moveMap = new LinkedHashMap<>();
        this.board = board;
    }

    /**
     * @return the game that this is a position in
     */
    public Game getOwnerGame() {
        return ownerGame;
    }

    /**
     * @return the current move number
     */
    public int getMoveNumber() {
        return moveNo;
    }

    /**
     * @return the last move made, or null if this is the starting position.
     */
    public Move getLastMove() {
        return lastMove;
    }

    /**
     * @return the current game position
     */
    public Board getPosition() {
        return board;
    }

    /**
     * @return the color of the player to move
     */
    public Piece.PieceColor getPlayerToMove() {
        return getPosition().getToMove();
    }

    /**
     * @return the entered moves in this position. The first move is the main move.
     */
    public List<Move> getMoves() {
        return new ArrayList<>(moveMap.keySet());
    }

    /**
     * @return the main move in the current position, or null if no more moves have been made
     */
    public Move getMainMove() {
        if (moveMap.size() == 0) return null;
        return moveMap.keySet().iterator().next();
    }

    /**
     * @return true if the current position has more moves than just the main move
     */
    public boolean hasVariations() {
        return moveMap.size() > 1;
    }

    /**
     * @return true if this is the last game position in a variation
     */
    public boolean isEndOfVariation() {
        return moveMap.size() == 0;
    }

    /**
     * Adds a move to a game position (if it hasn't already been added) and returns the position resulting after that.
     *
     * @param move the move to add. It's assumed that the move is pseudolegal.
     * @return the new game position
     */
    public GamePosition addMove(Move move) {
        if (moveMap.containsKey(move))
            return moveMap.get(move);
        GamePosition pos = new GamePosition(this, move);
        moveMap.put(move, pos);
        return pos;
    }

    /**
     * Replaces the current move(s) with the new move. All variations from the deleted moves will be lost.
     *
     * @param move the move to replace the existing ones with. It's assumed that the move is pseudolegal.
     * @return the new game position
     */
    public GamePosition replaceMove(Move move) {
        moveMap.clear();
        return addMove(move);
    }

    /**
     * Deletes a move from the game position, if it exist.
     *
     * @param move the move to delete
     */
    public void deleteMove(Move move) {
        moveMap.remove(move);
    }

    /**
     * @return The next game position following the main variation, or null if end of variation reached.
     */
    public GamePosition moveForward() {
        if (moveMap.size() == 0) return null;
        return moveMap.values().iterator().next();
    }

    /**
     * Returns the next game position in the game, playing the specified move
     *
     * @param move the move to play
     * @return the next game position after the played move
     * @throws RuntimeException if the specified move has not been added
     */
    public GamePosition moveForward(Move move) {
        if (!moveMap.containsKey(move))
            throw new RuntimeException("yarin.chess.Move does not exist");
        return moveMap.get(move);
    }

    /**
     * Returns the previous game position in the game
     *
     * @return the previous game position, or null if start of game reached.
     */
    public GamePosition moveBackward() {
        return previousPosition;
    }

    /**
     * Returns the start of the current variation
     *
     * @return the position that is the start of the variation
     */
    public GamePosition getVariationStart() {
        GamePosition pos = this;
        while (true) {
            if (pos.getLastMove() == null)
                return this;
            if (!pos.previousPosition.getMainMove().equals(pos.getLastMove()))
                return this;
            pos = pos.moveBackward();
        }
    }

    /**
     * Deletes the variation this move is in. If the position belongs to the main variation of the game,
     * nothing will be done.
     *
     * @return the position from where this variation started or null if the position belonged to the main variation.
     */
    public GamePosition deleteVariation() {
        GamePosition start = getVariationStart();
        if (start.getLastMove() == null)
            return null;
        GamePosition previous = start.moveBackward();
        previous.deleteMove(start.getLastMove());
        return previous;
    }

    /**
     * Makes the variation with the current move the primary variation.
     */
    public void promoteVariation() {
        GamePosition start = getVariationStart();
        if (start.getLastMove() == null)
            return;

        LinkedHashMap<Move, GamePosition> newMoveMap = new LinkedHashMap<>();
        newMoveMap.put(start.getLastMove(), start);
        for (Map.Entry<Move, GamePosition> de : start.moveBackward().moveMap.entrySet()) {
            if (!start.getLastMove().equals(de.getKey())) {
                newMoveMap.put(de.getKey(), de.getValue());
            }
        }
        start.moveBackward().moveMap = newMoveMap;
    }

    protected void convertToPGN(StringBuilder gameString) {
        boolean first = true;

        GamePosition gp = this;

        while (!gp.isEndOfVariation()) {
            if (getOwnerGame().getPreMoveComment(gp) != null)
                gameString.append(getOwnerGame().getPreMoveComment(gp));

            if (first || gp.getPlayerToMove() == Piece.PieceColor.WHITE) {
                gameString.append(String.format("{0}.", gp.getMoveNumber()));
                if (gp.getPlayerToMove() == Piece.PieceColor.BLACK)
                    gameString.append("..");
                first = false;
                gameString.append(" ");
            }

            gameString.append(gp.getMainMove().toString(gp.getPosition()));

            if (getOwnerGame().getPostMoveComment(gp) != null)
                gameString.append(getOwnerGame().getPostMoveComment(gp));

            GamePosition next = gp.moveForward();
            if (!next.isEndOfVariation())
                gameString.append(" ");

            if (gp.hasVariations()) {
                List<Move> moves = gp.getMoves();
                for (int i = 1; i < moves.size(); i++) {
                    gameString.append("(");
                    gp.moveForward(moves.get(i)).convertToPGN(gameString);
                    gameString.append(") ");
                }
            }

            gp = next;
        }
    }
}
