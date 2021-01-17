package se.yarin.cbhlib.games.search;

import se.yarin.cbhlib.Database;
import se.yarin.cbhlib.entities.TournamentEntity;
import se.yarin.cbhlib.entities.TournamentSearcher;
import se.yarin.cbhlib.games.GameHeader;

public class TournamentFilter extends SearchFilterBase {
    private final TournamentSearcher tournamentSearcher;

    public TournamentFilter(Database database, TournamentSearcher tournamentSearcher) {
        super(database);
        this.tournamentSearcher = tournamentSearcher;
    }

    @Override
    public boolean matches(GameHeader gameHeader) {
        TournamentEntity tournament = getDatabase().getTournamentBase().get(gameHeader.getTournamentId());
        return tournamentSearcher.matches(tournament);
    }
}
