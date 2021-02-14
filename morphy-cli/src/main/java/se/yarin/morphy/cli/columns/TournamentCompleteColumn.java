package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentEntity;

public class TournamentCompleteColumn implements TournamentColumn {
    @Override
    public String getHeader() {
        return "Complete";
    }

    @Override
    public String getTournamentValue(Database db, TournamentEntity tournament) {
        return tournament.isComplete() ? "Yes" : "-";
    }

    @Override
    public String getTournamentId() {
        return "complete";
    }
}
