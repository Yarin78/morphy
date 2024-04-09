package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Annotator;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.games.filters.AnnotatorFilter;
import se.yarin.morphy.games.filters.GameFilter;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QGamesByAnnotators extends ItemQuery<Game> {
    private final @NotNull ItemQuery<Annotator> annotatorQuery;
    private @Nullable List<Annotator> annotatorResult;
    private @Nullable AnnotatorFilter annotatorFilter;

    public QGamesByAnnotators(@NotNull ItemQuery<Annotator> annotatorQuery) {
        this.annotatorQuery = annotatorQuery;
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Game game) {
        evaluateSubQuery(txn);
        assert annotatorFilter != null;
        return annotatorFilter.matches(game.id(), game.header());
    }

    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        if (annotatorResult == null) {
            return INFINITE;
        }
        return annotatorResult.stream().map(Entity::count).reduce(0, Integer::sum);
    }

    public void evaluateSubQuery(@NotNull DatabaseReadTransaction txn) {
        if (annotatorResult == null) {
            annotatorResult = annotatorQuery.stream(txn).collect(Collectors.toList());
            annotatorFilter = new AnnotatorFilter(annotatorResult);
        }
    }

    @Override
    public @NotNull Stream<Game> stream(@NotNull DatabaseReadTransaction txn) {
        evaluateSubQuery(txn);
        assert annotatorFilter != null;
        return txn.stream(GameFilter.of(annotatorFilter, null));
    }
}
