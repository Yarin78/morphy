package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Game;

public class DateColumn implements GameColumn {
    @Override
    public String getHeader() {
        return "Date";
    }

    @Override
    public int width() {
        return 10;
    }

    @Override
    public String getValue(Game game) {
        return game.getPlayedDate().toPrettyString();
    }

    @Override
    public String getId() {
        return "date";
    }
}
