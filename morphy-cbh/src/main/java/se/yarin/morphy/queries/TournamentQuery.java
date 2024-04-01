package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.EntityFilter;

import java.util.List;

public class TournamentQuery extends EntityQuery<Tournament> {

    public TournamentQuery(@NotNull Database database, @NotNull List<EntityFilter<Tournament>> filters) {
        this(database, filters, null);
    }

    public TournamentQuery(@NotNull Database database, @NotNull List<EntityFilter<Tournament>> filters, @Nullable GameQuery gameQuery) {
        this(database, filters, gameQuery, null, 0);
    }

    public TournamentQuery(@NotNull Database database,
                           @NotNull List<EntityFilter<Tournament>> filters,
                           @Nullable GameQuery gameQuery,
                           @Nullable QuerySortOrder<Tournament> sortOrder,
                           int limit) {
        super(database, filters, gameQuery, sortOrder, limit);
    }
}
