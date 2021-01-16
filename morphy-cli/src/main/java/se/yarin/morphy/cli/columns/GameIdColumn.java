package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.chess.GameModel;

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
    public String getValue(Database database, GameHeader header, GameModel game) {
        return String.format("%8d", header.getId());
    }

    @Override
    public String getId() {
        return "id";
    }
}
