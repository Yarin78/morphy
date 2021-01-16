package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.entities.TournamentEntity;

public class TournamentPlaceColumn extends TournamentBaseColumn {
    @Override
    public String getHeader() {
        return "Place";
    }

    @Override
    public int width() {
        return 20;
    }

    @Override
    public String getId() {
        return "tournament-place";
    }

    @Override
    protected String getTournamentValue(TournamentEntity tournament) {
        return tournament.getPlace();
    }
}
