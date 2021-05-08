package se.yarin.morphy.cli.old.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentEntity;

public class TournamentFirstGameColumn implements TournamentColumn {
    @Override
    public String getHeader() {
        return "First  ";
    }

    @Override
    public String getTournamentValue(Database db, TournamentEntity tournament) {
        return String.format("%7d", tournament.getFirstGameId());
    }

    @Override
    public String getTournamentId() {
        return "first";
    }
}
