package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.chess.GameModel;

public class SourceColumn implements GameColumn {
    @Override
    public String getHeader() {
        return "Source";
    }

    @Override
    public int width() {
        return 20;
    }

    @Override
    public String getValue(Database database, GameHeader header, GameModel game) {
        int sourceId = header.getSourceId();
        if (sourceId <= 0) {
            return "";
        }
        return database.getSourceBase().get(sourceId).getTitle();
    }

    @Override
    public String getId() {
        return "source";
    }
}
