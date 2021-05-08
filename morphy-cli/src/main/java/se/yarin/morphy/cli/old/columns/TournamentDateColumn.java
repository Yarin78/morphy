package se.yarin.morphy.cli.old.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentEntity;

public class TournamentDateColumn extends TournamentBaseColumn {
    @Override
    public String getHeader() {
        return "Start date";
    }

    @Override
    public int width() {
        return 10;
    }

    @Override
    public String getTournamentValue(Database database, TournamentEntity tournament) {
        return tournament.getDate().toPrettyString();
    }

    @Override
    public String getId() {
        return "tournament-date";
    }
}
