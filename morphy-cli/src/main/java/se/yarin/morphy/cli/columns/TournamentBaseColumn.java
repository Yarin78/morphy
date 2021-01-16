package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentEntity;
import se.yarin.cbhlib.games.GameHeader;
import se.yarin.chess.GameModel;

public abstract class TournamentBaseColumn implements GameColumn {

    @Override
    public String getValue(Database database, GameHeader header, GameModel game) {
        int tournamentId = header.getTournamentId();
        if (tournamentId < 0) {
            return "";
        }
        return getTournamentValue(database.getTournamentBase().get(header.getTournamentId()));
    }

    protected abstract String getTournamentValue(TournamentEntity tournament);
}
