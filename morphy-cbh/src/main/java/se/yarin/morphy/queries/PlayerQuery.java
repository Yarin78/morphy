package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.ManualFilter;

import java.util.List;

public class PlayerQuery {
    private final @NotNull Database database;
    private final @NotNull List<EntityFilter<Player>> filters;

    // These are either both set or both null
    private final @Nullable GameQuery gameQuery;
    private final @Nullable GamePlayerJoinCondition joinCondition;

    public @NotNull Database database() {
        return database;
    }

    public @NotNull List<EntityFilter<Player>> filters() {
        return filters;
    }

    public @Nullable GameQuery gameQuery() {
        return gameQuery;
    }

    public @Nullable GamePlayerJoinCondition joinCondition() {
        return joinCondition;
    }

    public static PlayerQuery manual(@NotNull Database database, @NotNull List<Player> players) {
        return new PlayerQuery(database, List.of(new ManualFilter<>(players, EntityType.PLAYER)));
    }

    public PlayerQuery(@NotNull Database database, @Nullable List<EntityFilter<Player>> filters) {
        this.database = database;
        this.filters = filters == null ? List.of() : List.copyOf(filters);
        this.gameQuery = null;
        this.joinCondition = null;
    }

    public PlayerQuery(@NotNull Database database,
                       @Nullable List<EntityFilter<Player>> filters,
                       @NotNull GameQuery gameQuery,
                       // TODO: Should be PlayerGameJoinCondition
                       @NotNull GamePlayerJoinCondition joinCondition) {
        this.database = database;
        this.filters = filters == null ? List.of() : List.copyOf(filters);
        this.gameQuery = gameQuery;
        this.joinCondition = joinCondition;
    }
}
