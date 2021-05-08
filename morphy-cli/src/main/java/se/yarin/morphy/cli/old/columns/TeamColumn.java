package se.yarin.morphy.cli.old.columns;

import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.entities.TeamEntity;

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
    public String getValue(Game game) {
        if (game.isGuidingText()) {
            return "";
        }
        TeamEntity team = isWhite ? game.getWhiteTeam() : game.getBlackTeam();
        return team == null ? "" : team.getTitle();
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
