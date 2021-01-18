package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.games.ExtendedGameHeader;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.chess.GameModel;

import java.text.SimpleDateFormat;

public class CreationTimestampColumn implements GameColumn {

    @Override
    public String getHeader() {
        return "Created";
    }

    @Override
    public String getValue(Database database, GameHeader header, GameModel game) {
        ExtendedGameHeader extendedGameHeader = database.getExtendedHeaderBase().getExtendedGameHeader(header.getId());
        if (extendedGameHeader.getCreationTimestamp() == 0) {
            return "";
        }
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return df.format(extendedGameHeader.getCreationTime().getTime());
    }

    @Override
    public String getId() {
        return "created";
    }

    @Override
    public int width() {
        return 19;
    }
}
