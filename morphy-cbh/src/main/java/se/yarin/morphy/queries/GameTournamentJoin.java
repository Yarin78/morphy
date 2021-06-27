package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.CombinedFilter;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.queries.operations.GameTournamentFilter;
import se.yarin.morphy.queries.operations.QueryOperator;

public class GameTournamentJoin extends GameEntityJoin<Tournament> {
    private final @NotNull TournamentQuery tournamentQuery;

    public @NotNull TournamentQuery query() {
        return tournamentQuery;
    }

    public GameTournamentJoin(@NotNull TournamentQuery tournamentQuery) {
        this.tournamentQuery = tournamentQuery;
    }

    @Override
    QueryOperator<Game> applyGameFilter(@NotNull QueryContext context, @NotNull QueryOperator<Game> gameOperator) {
        if (tournamentQuery.gameQuery() != null) {
            throw new IllegalStateException("Can only apply filter joins at this stage");
        }
        EntityFilter<Tournament> filter = CombinedFilter.combine(tournamentQuery.filters());
        return filter == null ? gameOperator : new GameTournamentFilter(context, gameOperator, filter);
    }
}
