package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.PlayerEntity;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.chess.GameModel;

public class NameColumn implements GameColumn {

    private final boolean isWhite;

    public NameColumn(boolean isWhite) {
        this.isWhite = isWhite;
    }

    @Override
    public String getHeader() {
        return isWhite ? "White" : "  Black";
    }

    @Override
    public int marginRight() {
        return 2;
    }

    @Override
    public String getValue(Database database, GameHeader header, GameModel game) {
        if (header.isGuidingText()) {
            return isWhite ? "?" : "";
        }
        int playerId = isWhite ? header.getWhitePlayerId() : header.getBlackPlayerId();
        PlayerEntity player = database.getPlayerBase().get(playerId);
        String name = player.getFullNameShort();
        return isWhite ? name : ("- " + name);
    }

    @Override
    public String getId() {
        return "name";
    }

    @Override
    public int width() {
        return isWhite ? 20 : 22;
    }
}
