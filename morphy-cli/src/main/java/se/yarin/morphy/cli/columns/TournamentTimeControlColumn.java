package se.yarin.morphy.cli.columns;

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
    protected String getTournamentValue(TournamentEntity tournament) {
        return tournament.getTimeControl().getName();
    }
}
