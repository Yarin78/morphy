package se.yarin.morphy.cli.old.columns;

import se.yarin.cbhlib.Database;
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
    public String getTournamentValue(Database database, TournamentEntity tournament) {
        return tournament.getPlace();
    }
}
