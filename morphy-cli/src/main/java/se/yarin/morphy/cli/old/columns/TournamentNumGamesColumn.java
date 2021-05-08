package se.yarin.morphy.cli.old.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentEntity;

public class TournamentNumGamesColumn implements TournamentColumn {
    @Override
    public String getHeader() {
        return "Count";
    }

    @Override
    public String getTournamentValue(Database db, TournamentEntity tournament) {
        return String.format("%4d ", tournament.getCount());
    }

    @Override
    public String getTournamentId() {
        return "count";
    }
}
