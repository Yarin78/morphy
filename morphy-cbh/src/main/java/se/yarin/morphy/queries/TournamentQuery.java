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

    public @NotNull Database database() {
        return database;
    }

    public @NotNull List<EntityFilter<Tournament>> filters() {
        return filters;
    }

    public @Nullable GameQuery gameQuery() {
        return gameQuery;
    }

    public TournamentQuery(@NotNull Database database, @NotNull List<EntityFilter<Tournament>> filters) {
        this(database, filters, null);
    }

    public TournamentQuery(@NotNull Database database, @NotNull List<EntityFilter<Tournament>> filters, @Nullable GameQuery gameQuery) {
        this.database = database;
        this.filters = filters;
        this.gameQuery = gameQuery;
    }
}
