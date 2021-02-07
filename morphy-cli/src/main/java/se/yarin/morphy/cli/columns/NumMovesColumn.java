package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Game;

public class NumMovesColumn implements GameColumn {
    @Override
    public String getHeader() {
        return "Mov";
    }

    @Override
    public String getValue(Game game) {
        String numMoves = "";
        if (!game.isGuidingText()) {
            numMoves = game.getNoMoves() > 0 ? Integer.toString(game.getNoMoves()) : "";
        }
        return String.format("%3s", numMoves);
    }

    @Override
    public String getId() {
        return "num-moves";
    }
}
