package se.yarin.morphy.cli.old.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentEntity;

public class TournamentTitleColumn extends TournamentBaseColumn {
    @Override
    public String getHeader() {
        return "Tournament";
    }

    @Override
    public int width() {
        return 30;
    }

    @Override
    public int marginLeft() {
        return 2;
    }

    @Override
    public int marginRight() {
        return 2;
    }

    @Override
    public String getTournamentValue(Database database, TournamentEntity tournament) {
        return tournament.getTitle();
    }

    @Override
    public String getId() {
        return "tournament";
    }

    @Override
    public String getTournamentId() {
        return "title";
    }
}
