package se.yarin.chess;

public enum Player {
    WHITE(0),
    BLACK(1),
    NOBODY(-1);

    private final int value;

    public Player otherPlayer() {
        switch (this) {
            case WHITE:  return Player.BLACK;
            case BLACK:  return Player.WHITE;
            case NOBODY: return Player.NOBODY;
        }
        throw new RuntimeException("Invalid player: " + this);
    }

    Player(int value) {
        this.value = value;
    }
}
