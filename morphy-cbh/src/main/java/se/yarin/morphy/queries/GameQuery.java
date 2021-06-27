package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Database;
import se.yarin.morphy.entities.Player;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.entities.filters.CombinedFilter;
import se.yarin.morphy.entities.filters.EntityFilter;
import se.yarin.morphy.games.filters.GameFilter;

import java.util.ArrayList;
import java.util.List;

public class GameQuery {
    private final @NotNull Database database;
    private final @NotNull List<GameFilter> gameFilters;

    private final @NotNull List<GamePlayerJoin> playerJoins;
    private final @NotNull List<GameTournamentJoin> tournamentJoins;

    public GameQuery(@NotNull Database database,
                     @Nullable List<GameFilter> gameFilters) {
        this(database, gameFilters, null, null);
    }

    public GameQuery(@NotNull Database database,
                     @Nullable List<GameFilter> gameFilters,
                     @Nullable List<GamePlayerJoin> playerJoins,
                     @Nullable List<GameTournamentJoin> tournamentJoins) {
        this.database = database;
        this.gameFilters = gameFilters == null ? List.of() : List.copyOf(gameFilters);

        this.playerJoins = playerJoins == null ? List.of() : List.copyOf(playerJoins);
        this.tournamentJoins = tournamentJoins == null ? List.of() : List.copyOf(tournamentJoins);
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

    public List<GameEntityJoin<?>> entityJoins(boolean filtersOnly) {
        ArrayList<GameEntityJoin<?>> joins = new ArrayList<>();
        for (GamePlayerJoin playerJoin : playerJoins) {
            if (playerJoin.query().filters().size() > 0 || !filtersOnly) {
                joins.add(playerJoin);
            }
        }
        for (GameTournamentJoin tournamentJoin : tournamentJoins) {
            if (tournamentJoin.query().filters().size() > 0 || !filtersOnly) {
                joins.add(tournamentJoin);
            }
        }
/*
        for (GamePlayerJoin playerJoin : playerJoins) {
            EntityFilter<Player> playerFilter = CombinedFilter.combine(playerJoin.query().filters());
            if (playerFilter != null) {
                filters.add(playerFilter);
            }
        }

        for (GameTournamentJoin tournamentJoin : tournamentJoins) {
            EntityFilter<Tournament> tournamentFilter = CombinedFilter.combine(tournamentJoin.query().filters());
            if (tournamentFilter != null) {
                filters.add(tournamentFilter);
            }
        }

        return filters;
         */

        return joins;
    }
}
