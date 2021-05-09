package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Game;

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
        if (game.round() == 0) {
            return "";
        }
        if (game.subRound() == 0) {
            return String.format("%4d", game.round());
        }
        return String.format("%2d.%d", game.round(), game.subRound());
    }

    @Override
    public String getId() {
        return "round";
    }
}
