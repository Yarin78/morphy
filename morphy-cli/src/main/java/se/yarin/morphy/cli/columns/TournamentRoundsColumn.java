package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentEntity;

public class TournamentRoundsColumn extends TournamentBaseColumn {
    @Override
    public String getHeader() {
        return "Rnds";
    }

    @Override
    public int width() {
        return 5;
    }

    @Override
    public String getId() {
        return "tournament-rounds";
    }

    @Override
    public String getTournamentValue(Database database, TournamentEntity tournament) {
        return String.format("%3d", tournament.getRounds());
    }
}
