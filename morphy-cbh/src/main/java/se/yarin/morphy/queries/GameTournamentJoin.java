package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;

public class GameTournamentJoin {
    private final @NotNull TournamentQuery tournamentQuery;

    public @NotNull TournamentQuery query() {
        return tournamentQuery;
    }

    public GameTournamentJoin(@NotNull TournamentQuery tournamentQuery) {
        this.tournamentQuery = tournamentQuery;
    }
}
