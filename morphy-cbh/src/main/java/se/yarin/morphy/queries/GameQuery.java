package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.exceptions.MorphyNotSupportedException;
import se.yarin.morphy.games.filters.GameFilter;

import java.util.ArrayList;
import java.util.List;

public class GameQuery {
    private final @NotNull Database database;
    private final @NotNull List<GameFilter> gameFilters;
    // These filters generally can't be combined; one filter can be "Player starts with A"
    // and another "Player starts with B" which is fine since a game has two players.
    private final @NotNull List<? extends EntityFilter<Player>> playerFilters;
    private final @NotNull List<? extends EntityFilter<Tournament>> tournamentFilters;
    private final @NotNull List<? extends EntityQuery<?>> entityQueries;

    public GameQuery(@NotNull Database database,
                     @Nullable List<GameFilter> gameFilters) {
        this(database, gameFilters, null, null);
    }

    public GameQuery(@NotNull Database database,
                     @Nullable List<GameFilter> gameFilters,
                     @Nullable List<? extends EntityFilter<?>> entityFilter) {
        this(database, gameFilters, entityFilter, null);
    }

    public GameQuery(@NotNull Database database,
                     @Nullable List<GameFilter> gameFilters,
                     @Nullable List<? extends EntityFilter<?>> entityFilter,
                     @Nullable List<? extends EntityQuery<?>> entityQueries) {
        this.database = database;
        this.gameFilters = gameFilters == null ? List.of() : List.copyOf(gameFilters);

        ArrayList<EntityFilter<Player>> playerFilters = new ArrayList<>();
        ArrayList<EntityFilter<Tournament>> tournamentFilters = new ArrayList<>();
        if (entityFilter != null) {
            for (EntityFilter<?> filter : entityFilter) {
                if (filter.entityType() == EntityType.PLAYER) {
                    playerFilters.add((EntityFilter<Player>) filter);
                } else if (filter.entityType() == EntityType.TOURNAMENT) {
                    tournamentFilters.add((EntityFilter<Tournament>) filter);
                } else {
                    throw new MorphyNotSupportedException();
                }
            }
        }
        this.playerFilters = playerFilters;
        this.tournamentFilters = tournamentFilters;
        this.entityQueries = entityQueries == null ? List.of() : List.copyOf(entityQueries);

        // TODO: If there are entity queries that could be moved to entity filters, then do it!
    }

    public @NotNull Database database() {
        return database;
    }

    public @NotNull List<GameFilter> gameFilters() {
        return gameFilters;
    }

    public @NotNull List<? extends EntityFilter<Player>> playerFilters() {
        return playerFilters;
    }

    public @NotNull List<? extends EntityFilter<Tournament>> tournamentFilters() {
        return tournamentFilters;
    }

    public @NotNull List<EntityFilter<?>> entityFilters() {
        ArrayList<EntityFilter<?>> entityFilters = new ArrayList<>();
        entityFilters.addAll(playerFilters);
        entityFilters.addAll(tournamentFilters);
        return entityFilters;
    }

    public @NotNull List<? extends EntityQuery<?>> entityQueries() {
        return entityQueries;
    }
}
