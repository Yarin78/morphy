package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Game;

public class GameIdColumn implements GameColumn {

    @Override
    public String getHeader() {
        return "  Game #";
    }

    @Override
    public int marginRight() {
        return 2;
    }

    @Override
    public String getValue(Game game) {
        return String.format("%8d", game.id());
    }

    @Override
    public String getId() {
        return "id";
    }
}
