package se.yarin.chess;

import lombok.Getter;

/**
 * Thrown when an illegal move is being added to a {@link GameMovesModel}
 *
 * This is a {@link RuntimeException} because it shouldn't happen if you use the API's properly.
 */
public class IllegalMoveException extends RuntimeException {

    @Getter
    private final Position position;

    @Getter
    private final Move move;

    public IllegalMoveException(Position position, Move move) {
        this.position = position;
        this.move = move;
    }

    public IllegalMoveException(Position position, Move move, String message) {
        super(message);
        this.position = position;
        this.move = move;
    }

    @Override
    public String toString() {
        String s = "Position: " + position + ", move: " + move;
        if (getMessage() != null) {
            s = getMessage() + ": " + s;
        }
        return s;
    }
}
