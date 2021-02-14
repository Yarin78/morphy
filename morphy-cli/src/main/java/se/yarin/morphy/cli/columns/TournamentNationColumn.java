package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentEntity;

public class TournamentNationColumn extends TournamentBaseColumn {
    @Override
    public String getHeader() {
        return "Nation";
    }

    @Override
    public int width() {
        return 20;
    }

    @Override
    public String getId() {
        return "tournament-nation";
    }

    @Override
    public String getTournamentValue(Database database, TournamentEntity tournament) {
        return tournament.getNation().getName();
    }
}
