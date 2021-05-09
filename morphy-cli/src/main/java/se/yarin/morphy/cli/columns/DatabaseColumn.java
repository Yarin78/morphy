package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Database;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Tournament;

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
    public String getTournamentValue(Database db, Tournament tournament) {
        return db.name();
    }

    @Override
    public String getValue(Game game) {
        return game.database().name();
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
