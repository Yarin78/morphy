package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.entities.TournamentEntity;

public class TournamentTypeColumn extends TournamentBaseColumn {
    @Override
    public String getHeader() {
        return "Type";
    }

    @Override
    public int width() {
        return 5;
    }

    @Override
    public String getId() {
        return "tournament-type";
    }

    @Override
    protected String getTournamentValue(TournamentEntity tournament) {
        return tournament.getType().getName();
    }
}
