package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Game;

public class GameVersionColumn implements GameColumn {

    @Override
    public String getHeader() {
        return "Ver";
    }

    @Override
    public String getValue(Game game) {
        return String.format("%3d", game.getGameVersion());
    }

    @Override
    public String getId() {
        return "game-version";
    }
}
