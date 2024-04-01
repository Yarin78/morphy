package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.EntityType;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.entities.filters.ManualFilter;

import java.util.List;

public class PlayerQuery extends EntityQuery<Player> {
    private final @Nullable GamePlayerJoinCondition joinCondition;


    public @Nullable GamePlayerJoinCondition joinCondition() {
        return joinCondition;
    }


    public static PlayerQuery manual(@NotNull Database database, @NotNull List<Player> players) {
        return new PlayerQuery(database, List.of(new ManualFilter<>(players, EntityType.PLAYER)));
    }

    public PlayerQuery(@NotNull Database database, @Nullable List<EntityFilter<Player>> filters) {
        this(database, filters, null, 0);
    }

    public PlayerQuery(@NotNull Database database,
                       @Nullable List<EntityFilter<Player>> filters,
                       @Nullable QuerySortOrder<Player> sortOrder,
                       int limit) {
        this(database, filters, null, null, sortOrder, limit);
    }

    public PlayerQuery(@NotNull Database database,
                       @Nullable List<EntityFilter<Player>> filters,
                       @NotNull GameQuery gameQuery,
                       @NotNull GamePlayerJoinCondition joinCondition) {
        this(database, filters, gameQuery, joinCondition, null, 0);
    }

    public PlayerQuery(@NotNull Database database,
                       @Nullable List<EntityFilter<Player>> filters,
                       @Nullable GameQuery gameQuery,
                       @Nullable GamePlayerJoinCondition joinCondition,
                       @Nullable QuerySortOrder<Player> sortOrder,
                       int limit) {
        super(database, filters, gameQuery, sortOrder, limit);

        if ((gameQuery != null && joinCondition == null) || (gameQuery == null && joinCondition != null)) {
            throw new IllegalArgumentException("joinCondition must be set if and only if gameQuery is set");
        }
        this.joinCondition = joinCondition;
    }
}
