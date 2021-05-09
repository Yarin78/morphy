package se.yarin.morphy.cli.columns;

import se.yarin.morphy.Database;
import se.yarin.morphy.entities.Tournament;

public class TournamentFirstGameColumn implements TournamentColumn {
    @Override
    public String getHeader() {
        return "First  ";
    }

    @Override
    public String getTournamentValue(Database db, Tournament tournament) {
        return String.format("%7d", tournament.firstGameId());
    }

    @Override
    public String getTournamentId() {
        return "first";
    }
}
