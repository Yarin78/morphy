package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Database;
import se.yarin.morphy.entities.Tournament;

public class TournamentTypeColumn extends TournamentBaseColumn {
    @Override
    public String getHeader() {
        return "Type";
    }

    @Override
    public int width() {
        return 25;
    }

    @Override
    public String getId() {
        return "tournament-type";
    }

    @Override
    public String getTournamentValue(Database database, Tournament tournament) {
        return tournament.getPrettyTypeName();
    }
}
