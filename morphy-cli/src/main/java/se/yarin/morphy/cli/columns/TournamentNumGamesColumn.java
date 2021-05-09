package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Database;
import se.yarin.morphy.entities.Tournament;

public class TournamentNumGamesColumn implements TournamentColumn {
    @Override
    public String getHeader() {
        return "Count";
    }

    @Override
    public String getTournamentValue(Database db, Tournament tournament) {
        return String.format("%4d ", tournament.count());
    }

    @Override
    public String getTournamentId() {
        return "count";
    }
}
