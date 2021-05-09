package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Game;

public class NumMovesColumn implements GameColumn {
    @Override
    public String getHeader() {
        return "Mov";
    }

    @Override
    public String getValue(Game game) {
        String numMoves = "";
        if (!game.guidingText()) {
            numMoves = game.noMoves() > 0 ? Integer.toString(game.noMoves()) : "";
        }
        return String.format("%3s", numMoves);
    }

    @Override
    public String getId() {
        return "num-moves";
    }
}
