package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.Tournament;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.games.filters.TournamentFilter;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QGamesByTournaments extends ItemQuery<Game> {
    private final @NotNull ItemQuery<Tournament> tournamentQuery;
    private @Nullable List<Tournament> tournamentResult;
    private @Nullable TournamentFilter tournamentFilter;

    public QGamesByTournaments(@NotNull ItemQuery<Tournament> tournamentQuery) {
        this.tournamentQuery = tournamentQuery;
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Game game) {
        evaluateSubQuery(txn);
        assert tournamentFilter != null;
        return tournamentFilter.matches(game.id(), game.header());
    }

    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        if (tournamentResult == null) {
            return INFINITE;
        }
        return tournamentResult.stream().map(Entity::count).reduce(0, Integer::sum);
    }

    public void evaluateSubQuery(@NotNull DatabaseReadTransaction txn) {
        if (this.tournamentFilter == null) {
            this.tournamentResult = tournamentQuery.stream(txn).collect(Collectors.toList());
            this.tournamentFilter = new TournamentFilter(tournamentResult);
        }
    }

    @Override
    public @NotNull Stream<Game> stream(@NotNull DatabaseReadTransaction txn) {
        evaluateSubQuery(txn);
        assert tournamentFilter != null;
        return txn.stream(GameFilter.of(tournamentFilter, null));
    }
}
