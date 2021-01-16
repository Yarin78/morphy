package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.chess.GameModel;

public class NumMovesColumn implements GameColumn {
    @Override
    public String getHeader() {
        return "Mov";
    }

    @Override
    public String getValue(Database database, GameHeader header, GameModel game) {
        String numMoves = "";
        if (!header.isGuidingText()) {
            numMoves = header.getNoMoves() > 0 ? Integer.toString(header.getNoMoves()) : "";
        }
        return String.format("%3s", numMoves);
    }

    @Override
    public String getId() {
        return "moves";
    }
}
