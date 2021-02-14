package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentEntity;

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
    public String getTournamentValue(Database database, TournamentEntity tournament) {
        return tournament.getPrettyTypeName();
    }
}
