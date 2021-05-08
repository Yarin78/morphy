package se.yarin.morphy.queries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.Source;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.games.filters.SourceFilter;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QGamesBySource extends ItemQuery<Game> {
    private final @NotNull ItemQuery<Source> sourceQuery;
    private @Nullable List<Source> sourceResult;
    private @Nullable SourceFilter sourceFilter;

    public QGamesBySource(@NotNull ItemQuery<Source> sourceQuery) {
        this.sourceQuery = sourceQuery;
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Game game) {
        evaluateSubQuery(txn);
        assert sourceFilter != null;
        return sourceFilter.matches(game.header());
    }

    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        if (sourceResult == null) {
            return INFINITE;
        }
        return sourceResult.stream().map(Entity::count).reduce(0, Integer::sum);
    }

    public void evaluateSubQuery(@NotNull DatabaseReadTransaction txn) {
        if (sourceResult == null) {
            sourceResult = sourceQuery.stream(txn).collect(Collectors.toList());
            sourceFilter = new SourceFilter(sourceResult);
        }
    }

    @Override
    public @NotNull Stream<Game> stream(@NotNull DatabaseReadTransaction txn) {
        evaluateSubQuery(txn);
        assert sourceFilter != null;
        return txn.stream(GameFilter.of(sourceFilter, null));
    }
}
