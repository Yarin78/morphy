package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.Game;
import se.yarin.morphy.games.filters.GameFilter;

import java.util.ArrayList;
import java.util.List;

public class GameQuery {
    private final @NotNull Database database;
    private final @NotNull List<GameFilter> gameFilters;

    private final @NotNull List<GamePlayerJoin> playerJoins;
    private final @NotNull List<GameTournamentJoin> tournamentJoins;

    private final @Nullable QuerySortOrder<Game> sortOrder;
    private final int limit;  // 0 = all

    public GameQuery(@NotNull Database database,
                     @Nullable List<GameFilter> gameFilters) {
        this(database, gameFilters, null, null);
    }

    public GameQuery(@NotNull Database database,
                     @Nullable List<GameFilter> gameFilters,
                     @Nullable List<GamePlayerJoin> playerJoins,
                     @Nullable List<GameTournamentJoin> tournamentJoins) {
        this(database, gameFilters, playerJoins, tournamentJoins, null, 0);
    }

    public GameQuery(@NotNull Database database,
                     @Nullable List<GameFilter> gameFilters,
                     @Nullable List<GamePlayerJoin> playerJoins,
                     @Nullable List<GameTournamentJoin> tournamentJoins,
                     @Nullable QuerySortOrder<Game> sortOrder,
                     int limit) {
        this.database = database;
        this.gameFilters = gameFilters == null ? List.of() : List.copyOf(gameFilters);

        this.playerJoins = playerJoins == null ? List.of() : List.copyOf(playerJoins);
        this.tournamentJoins = tournamentJoins == null ? List.of() : List.copyOf(tournamentJoins);

        this.sortOrder = sortOrder;
        this.limit = limit;
    }

    public @NotNull Database database() {
        return database;
    }

    public @NotNull List<GameFilter> gameFilters() {
        return gameFilters;
    }

    public @NotNull List<GamePlayerJoin> playerJoins() {
        return playerJoins;
    }

    public @NotNull List<GameTournamentJoin> tournamentJoins() {
        return tournamentJoins;
    }

    public @Nullable QuerySortOrder<Game> sortOrder() {
        return sortOrder;
    }

    public int limit() {
        return limit;
    }

    public List<GameEntityJoin<?>> entityJoins(boolean filtersOnly) {
        ArrayList<GameEntityJoin<?>> joins = new ArrayList<>();
        for (GamePlayerJoin playerJoin : playerJoins) {
            if (!playerJoin.query().filters().isEmpty() || !filtersOnly) {
                joins.add(playerJoin);
            }
        }
        for (GameTournamentJoin tournamentJoin : tournamentJoins) {
            if (!tournamentJoin.query().filters().isEmpty() || !filtersOnly) {
                joins.add(tournamentJoin);
            }
        }

        return joins;
    }
}
