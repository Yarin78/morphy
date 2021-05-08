package se.yarin.morphy.cli.old.columns;

import se.yarin.cbhlib.Game;

public class RoundColumn implements GameColumn {
    @Override
    public String getHeader() {
        return "Rnd";
    }

    @Override
    public int width() {
        return 4;
    }

    @Override
    public String getValue(Game game) {
        if (game.getRound() == 0) {
            return "";
        }
        if (game.getSubRound() == 0) {
            return String.format("%4d", game.getRound());
        }
        return String.format("%2d.%d", game.getRound(), game.getSubRound());
    }

    @Override
    public String getId() {
        return "round";
    }
}
