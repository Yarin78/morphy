package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentEntity;

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
    public String getTournamentValue(Database database, TournamentEntity tournament) {
        return tournament.getTimeControl().getName();
    }
}
