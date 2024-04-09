package se.yarin.morphy.qqueries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.DatabaseReadTransaction;
import se.yarin.morphy.Game;
import se.yarin.morphy.entities.Entity;
import se.yarin.morphy.entities.GameTag;
import se.yarin.morphy.games.filters.GameFilter;
import se.yarin.morphy.games.filters.GameTagFilter;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QGamesByGameTag extends ItemQuery<Game> {
    private final @NotNull ItemQuery<GameTag> gameTagQuery;
    private @Nullable List<GameTag> gameTagResult;
    private @Nullable GameTagFilter gameTagFilter;

    public QGamesByGameTag(@NotNull ItemQuery<GameTag> gameTagQuery) {
        this.gameTagQuery = gameTagQuery;
    }

    @Override
    public boolean matches(@NotNull DatabaseReadTransaction txn, @NotNull Game game) {
        evaluateSubQuery(txn);
        assert gameTagFilter != null;
        return gameTagFilter.matches(game.id(), game.extendedHeader());
    }

    public int rowEstimate(@NotNull DatabaseReadTransaction txn) {
        if (gameTagResult == null) {
            return INFINITE;
        }
        return gameTagResult.stream().map(Entity::count).reduce(0, Integer::sum);
    }

    public void evaluateSubQuery(@NotNull DatabaseReadTransaction txn) {
        if (gameTagResult == null) {
            gameTagResult = gameTagQuery.stream(txn).collect(Collectors.toList());
            gameTagFilter = new GameTagFilter(gameTagResult);
        }
    }

    @Override
    public @NotNull Stream<Game> stream(@NotNull DatabaseReadTransaction txn) {
        evaluateSubQuery(txn);
        assert gameTagFilter != null;
        return txn.stream(GameFilter.of(null, gameTagFilter));
    }
}
