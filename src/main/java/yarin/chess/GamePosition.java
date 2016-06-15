package yarin.chess;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GamePosition represents a position in a recorded chess game or line.
 * It contains a list of moves (possibly 0) that moves the game forward.
 * The first move in this list is the main variation.
 */
public class GamePosition {
    private Game ownerGame;
    private List<GamePosition> nextPositions; // Positions in the game tree that occurs after this one

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
        nextPositions = new ArrayList<>();
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
        this.nextPositions = new ArrayList<>();
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
     * @return the entered moves in this position. The first move is the main move. There may be duplicate moves.
     */
    public List<Move> getMoves() {
        return nextPositions.stream().map(GamePosition::getLastMove).collect(Collectors.toList());
    }

    /**
     * @return a list of positions that can be reached from this position
     */
    public List<GamePosition> getForwardPositions() {
        return new ArrayList<>(nextPositions);
    }

    /**
     * @return the main move in the current position, or null if no more moves have been made
     */
    public Move getMainMove() {
        if (nextPositions.size() == 0) return null;
        return nextPositions.get(0).getLastMove();
    }

    /**
     * @return true if the current position has more moves than just the main move
     */
    public boolean hasVariations() {
        return nextPositions.size() > 1;
    }

    /**
     * @return true if this is the last game position in a variation
     */
    public boolean isEndOfVariation() {
        return nextPositions.size() == 0;
    }

    /**
     * @return true if there is a single sequence of moves from this position to the end of the line
     */
    public boolean isSingleLine() {
        return isEndOfVariation() || !hasVariations() && getForwardPosition().isSingleLine();
    }

    /**
     * Adds a move to a game position and returns the position resulting after that.
     * A new variation will be created even if the same move has been added before.
     *
     * @param move the move to add. It's assumed that the move is pseudolegal.
     * @return the new game position
     */
    public GamePosition addMove(Move move) {
        GamePosition pos = new GamePosition(this, move);
        nextPositions.add(pos);
        return pos;
    }

    /**
     * Replaces the current move(s) with the new move. All variations from the deleted moves will be lost.
     *
     * @param move the move to replace the existing ones with. It's assumed that the move is pseudolegal.
     * @return the new game position
     */
    public GamePosition replaceMove(Move move) {
        nextPositions = new ArrayList<>();
        return addMove(move);
    }

    /**
     * Deletes a succeeding game position, if it exist.
     *
     * @param position the succeeding position to delete
     */
    public boolean deleteForwardPosition(GamePosition position) {
        return nextPositions.remove(position);
    }

    /**
     * @return The next game position following the main variation, or null if end of variation reached.
     */
    public GamePosition getForwardPosition() {
        if (nextPositions.size() == 0) return null;
        return nextPositions.get(0);
    }

    /**
     * Returns the next game position in the game, playing the specified move
     * If there are multiple positions with the same move, the first one will be picked
     *
     * @param move the move to play
     * @return the next game position after the played move
     * @throws RuntimeException if the specified move has not been added
     */
    public GamePosition getForwardPosition(Move move) {
        for (GamePosition nextPosition : nextPositions) {
            if (nextPosition.getLastMove().equals(move)) {
                return nextPosition;
            }
        }
        throw new RuntimeException("Move does not exist");
    }

    /**
     * Returns the previous game position in the game
     *
     * @return the previous game position, or null if start of game reached.
     */
    public GamePosition getBackPosition() {
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
            if (pos.getBackPosition() == null)
                return this;
            if (pos.getBackPosition().getForwardPosition() != this)
                return this;
            pos = pos.getBackPosition();
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
        if (start.getBackPosition() == null)
            return null;
        GamePosition previous = start.getBackPosition();
        previous.deleteForwardPosition(start);
        return previous;
    }

    /**
     * Makes the variation with the current move the primary variation.
     */
    public void promoteVariation() {
        GamePosition start = getVariationStart();
        GamePosition parent = start.getBackPosition();
        if (parent == null)
            return;

        // TODO: The two variation should actually exchange places, to get the same behaviour as CB
        ArrayList<GamePosition> newPositions = new ArrayList<>();
        newPositions.add(start);
        newPositions.addAll(parent.getForwardPositions()
                .stream()
                .filter(pos -> pos != start)
                .collect(Collectors.toList()));
        parent.nextPositions = newPositions;
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

            GamePosition next = gp.getForwardPosition();
            if (!next.isEndOfVariation())
                gameString.append(" ");

            if (gp.hasVariations()) {
                List<Move> moves = gp.getMoves();
                for (int i = 1; i < moves.size(); i++) {
                    gameString.append("(");
                    gp.getForwardPosition(moves.get(i)).convertToPGN(gameString);
                    gameString.append(") ");
                }
            }

            gp = next;
        }
    }
}
