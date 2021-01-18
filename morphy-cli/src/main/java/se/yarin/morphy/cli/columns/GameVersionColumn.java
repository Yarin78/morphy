package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.games.ExtendedGameHeader;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.chess.GameModel;

public class GameVersionColumn implements GameColumn {

    @Override
    public String getHeader() {
        return "Ver";
    }

    @Override
    public String getValue(Database database, GameHeader header, GameModel game) {
        ExtendedGameHeader extendedGameHeader = database.getExtendedHeaderBase().getExtendedGameHeader(header.getId());
        return String.format("%3d", extendedGameHeader.getGameVersion());
    }

    @Override
    public String getId() {
        return "game-version";
    }
}
