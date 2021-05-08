package se.yarin.morphy.cli.old.columns;

import se.yarin.cbhlib.Game;

public class GameVersionColumn implements GameColumn {

    @Override
    public String getHeader() {
        return "Vers";
    }

    @Override
    public String getValue(Game game) {
        return String.format("%4d", game.getGameVersion());
    }

    @Override
    public String getId() {
        return "version";
    }
}
