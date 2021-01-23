package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Game;

public class DatabaseColumn implements GameColumn {
    @Override
    public String getHeader() {
        return "Database";
    }

    @Override
    public int width() {
        return 30;
    }

    @Override
    public String getValue(Game game) {
        return game.getDatabase().getHeaderBase().getStorageName();
    }

    @Override
    public String getId() {
        return "database";
    }
}
