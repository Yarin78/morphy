package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.chess.GameModel;

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
    public String getValue(Database database, GameHeader header, GameModel game) {
        if (header.getRound() == 0) {
            return "";
        }
        if (header.getSubRound() == 0) {
            return String.format("%4d", header.getRound());
        }
        return String.format("%2d.%d", header.getRound(), header.getSubRound());
    }

    @Override
    public String getId() {
        return "round";
    }
}
