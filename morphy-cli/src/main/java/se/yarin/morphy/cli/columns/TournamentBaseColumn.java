package se.yarin.morphy.cli.columns;

import se.yarin.cbhlib.Game;
import se.yarin.cbhlib.entities.TournamentEntity;

public abstract class TournamentBaseColumn implements GameColumn {

    @Override
    public String getValue(Game game) {
        return getTournamentValue(game.getTournament());
    }

    protected abstract String getTournamentValue(TournamentEntity tournament);
}
