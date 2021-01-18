package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.games.ExtendedGameHeader;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.chess.GameModel;

public class TeamColumn implements GameColumn {

    private final boolean isWhite;

    public TeamColumn(boolean isWhite) {
        this.isWhite = isWhite;
    }

    @Override
    public String getHeader() {
        return "Team";
    }

    @Override
    public int marginRight() {
        return 2;
    }

    @Override
    public String getValue(Database database, GameHeader header, GameModel game) {
        if (header.isGuidingText()) {
            return "";
        }
        ExtendedGameHeader extendedGameHeader = database.getExtendedHeaderBase().getExtendedGameHeader(header.getId());
        int teamId = isWhite ? extendedGameHeader.getWhiteTeamId() : extendedGameHeader.getBlackTeamId();
        if (teamId < 0) {
            return "";
        }
        return database.getTeamBase().get(teamId).getTitle();
    }

    @Override
    public String getId() {
        return "team";
    }

    @Override
    public int width() {
        return 20;
    }
}
