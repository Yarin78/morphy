package se.yarin.morphy.cli.old.columns;

import se.yarin.cbhlib.Game;

public class SourceColumn implements GameColumn {
    @Override
    public String getHeader() {
        return "Source";
    }

    @Override
    public int width() {
        return 20;
    }

    @Override
    public String getValue(Game game) {
        return game.getSource().getTitle();
    }

    @Override
    public String getId() {
        return "source";
    }
}
