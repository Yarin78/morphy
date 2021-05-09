package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Database;
import se.yarin.morphy.entities.Tournament;

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
    public String getTournamentValue(Database database, Tournament tournament) {
        return String.format("%3d", tournament.rounds());
    }
}
