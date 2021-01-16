package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.chess.GameModel;

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
    public String getValue(Database database, GameHeader header, GameModel game) {
        return header.getPlayedDate().toPrettyString();
    }

    @Override
    public String getId() {
        return "date";
    }
}
