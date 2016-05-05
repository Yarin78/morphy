package yarin.chess;

/**
 * Represents a chess game.
 *
 * This is the same as a yarin.chess.GamePosition with the exception that it has some extra data such as an artifical start position.
 */
public class Game extends GamePosition {
    private boolean setupPosition; // true if the game starts with a setup position

    /**
     * @return true if the game starts from a setup position
     */
    public boolean isSetupPosition() {
        return setupPosition;
    }

    public Game() {
        super(new Board(), 1);
        setupPosition = false;
    }

    public Game(Board b) {
        this(b, 1);
    }

    public Game(Board b, int moveNo) {
        super(b, moveNo);
        setupPosition = true;
    }

    public String toPGN() {
        StringBuilder gameString = new StringBuilder();
        convertToPGN(gameString);
        return gameString.toString();
    }

    protected String getPreMoveComment(GamePosition position) {
        return null;
    }

    protected String getPostMoveComment(GamePosition position) {
        return null;
    }
}
