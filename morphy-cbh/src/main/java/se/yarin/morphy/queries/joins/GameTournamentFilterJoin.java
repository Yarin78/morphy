package se.yarin.morphy.queries.joins;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityIndexReadTransaction;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.EntityFilter;

public class GameTournamentFilterJoin extends GameEntityFilterJoin<Tournament> {

    public GameTournamentFilterJoin(@NotNull EntityFilter<Tournament> entityFilter) {
        super(entityFilter);
    }

    @Override
    public boolean gameFilter(@NotNull Game game, @NotNull EntityIndexReadTransaction<Tournament> txn) {
        EntityFilter<Tournament> filter = getEntityFilter();
        Tournament tournament = txn.get(game.tournamentId(), filter);
        return tournament != null && filter.matches(tournament);
    }
}
