package se.yarin.chess;

public enum Player {
    WHITE(0),
    BLACK(1),
    NOBODY(-1);

    private final int value;

    public Player otherPlayer() {
        return switch (this) {
            case WHITE -> Player.BLACK;
            case BLACK -> Player.WHITE;
            case NOBODY -> Player.NOBODY;
        };
    }

    Player(int value) {
        this.value = value;
    }
}
