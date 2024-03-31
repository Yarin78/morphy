package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.EntityFilter;

import java.util.List;

public class TournamentQuery {
    private final @NotNull Database database;
    private final @NotNull List<EntityFilter<Tournament>> filters;

    private final @Nullable GameQuery gameQuery;

    private final @NotNull QuerySortOrder<Tournament> sortOrder;
    private final int limit;  // 0 = all

    public @NotNull Database database() {
        return database;
    }

    public @NotNull List<EntityFilter<Tournament>> filters() {
        return filters;
    }

    public @Nullable GameQuery gameQuery() {
        return gameQuery;
    }

    public @NotNull QuerySortOrder<Tournament> sortOrder() {
        return sortOrder;
    }

    public int limit() {
        return limit;
    }

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
        this.database = database;
        this.filters = filters;
        this.gameQuery = gameQuery;
        this.sortOrder = sortOrder == null ? QuerySortOrder.none() : sortOrder;
        this.limit = limit;
    }
}
