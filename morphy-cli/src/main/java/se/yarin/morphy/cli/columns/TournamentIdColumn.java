package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentEntity;

public class TournamentIdColumn implements TournamentColumn {
    @Override
    public String getHeader() {
        return "    #";
    }

    @Override
    public int marginRight() {
        return 2;
    }

    @Override
    public String getTournamentValue(Database db, TournamentEntity tournament) {
        return String.format("%5d", tournament.getId());
    }

    @Override
    public String getTournamentId() {
        return "id";
    }
}
