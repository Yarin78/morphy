package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.games.ExtendedGameHeader;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.chess.GameModel;

import java.text.SimpleDateFormat;

public class LastChangedTimestampColumn implements GameColumn {

    @Override
    public String getHeader() {
        return "Last changed";
    }

    @Override
    public String getValue(Database database, GameHeader header, GameModel game) {
        ExtendedGameHeader extendedGameHeader = database.getExtendedHeaderBase().getExtendedGameHeader(header.getId());
        if (extendedGameHeader.getLastChangedTimestamp() == 0) {
            return "";
        }
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return df.format(extendedGameHeader.getLastChangedTime().getTime());
    }

    @Override
    public String getId() {
        return "updated";
    }

    @Override
    public int width() {
        return 19;
    }
}
