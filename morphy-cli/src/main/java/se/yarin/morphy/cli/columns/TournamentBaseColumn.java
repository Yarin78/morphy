package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.entities.TournamentEntity;

public abstract class TournamentBaseColumn implements GameColumn, TournamentColumn {

    public int width() {
        return getHeader().length();
    }

    public int marginLeft() {
        return 1;
    }

    public int marginRight() {
        return 1;
    }

    public boolean trimValueToWidth() { return true; }

    @Override
    public String getValue(Game game) {
        return getTournamentValue(game.getDatabase(), game.getTournament());
    }

    public abstract String getTournamentValue(Database db, TournamentEntity tournament);

    @Override
    public String getTournamentId() {
        String id = getId();
        assert id.startsWith("tournament-") : id;
        return id.substring(11);
    }
}
