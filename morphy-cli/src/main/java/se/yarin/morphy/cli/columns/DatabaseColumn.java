package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.entities.TournamentEntity;

public class DatabaseColumn implements GameColumn, TournamentColumn {
    @Override
    public String getHeader() {
        return "Database";
    }

    @Override
    public int width() {
        return 30;
    }

    @Override
    public int marginLeft() {
        return 1;
    }

    @Override
    public int marginRight() {
        return 1;
    }

    @Override
    public boolean trimValueToWidth() {
        return true;
    }

    @Override
    public String getTournamentValue(Database db, TournamentEntity tournament) {
        return db.getHeaderBase().getStorageName();
    }

    @Override
    public String getValue(Game game) {
        return game.getDatabase().getHeaderBase().getStorageName();
    }

    @Override
    public String getTournamentId() {
        return "database";
    }

    @Override
    public String getId() {
        return "database";
    }
}
