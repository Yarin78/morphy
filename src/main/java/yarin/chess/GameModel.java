package yarin.chess;

/**
 * Contains metadata about the game (the @{@link GameMetaData}), the moves
 * (@{@link Game}) and the current selected move.
 */
public class GameModel {
    private Game game;
    private GameMetaData header;
    private GamePosition selectedMove;

    public GameModel() {
        this.game = new Game();
        this.header = new GameMetaData();
        this.selectedMove = null;
    }

    public GameModel(Game game, GameMetaData header, GamePosition selectedMove) {
        this.game = game;
        this.header = header;
        this.selectedMove = selectedMove;
    }

    public Game getGame() {
        return game;
    }

    public GameMetaData getHeader() {
        return header;
    }

    public GamePosition getSelectedMove() {
        return selectedMove;
    }

    public void setGame(Game game, GamePosition selectedMove) {
        if (selectedMove.getOwnerGame() != game)
            throw new IllegalArgumentException("The selected move must belong to the game");
        this.game = game;
        this.selectedMove = selectedMove;
    }

    public void setHeader(GameMetaData header) {
        this.header = header;
    }

    public void setSelectedMove(GamePosition selectedMove) {
        if (selectedMove.getOwnerGame() != game)
            throw new IllegalArgumentException("The selected move must belong to the game");
        this.selectedMove = selectedMove;
    }
}
