package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Database;
import se.yarin.morphy.entities.Tournament;

public class TournamentTimeControlColumn extends TournamentBaseColumn {
    @Override
    public String getHeader() {
        return "Time";
    }

    @Override
    public int width() {
        return 6;
    }

    @Override
    public String getId() {
        return "tournament-time";
    }

    @Override
    public String getTournamentValue(Database database, Tournament tournament) {
        return tournament.timeControl().getName();
    }
}
