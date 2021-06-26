package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.yarin.morphy.Game;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MergeGameJoin extends QueryOperator<Game> {
    private final @NotNull QueryOperator<Game> left;
    private final @Nullable QueryOperator<Integer> rightId;
    private final @Nullable QueryOperator<Game> rightGame;

    private MergeGameJoin(@NotNull QueryContext queryContext,
                          @NotNull QueryOperator<Game> left,
                          @Nullable QueryOperator<Integer> rightId,
                          @Nullable QueryOperator<Game> rightGame) {
        super(queryContext);
        this.left = left;
        this.rightId = rightId;
        this.rightGame = rightGame;
    }

    public static MergeGameJoin gameIdJoin(@NotNull QueryContext queryContext, @NotNull QueryOperator<Game> left, @NotNull QueryOperator<Integer> right) {
        return new MergeGameJoin(queryContext, left, right, null);
    }

    public static MergeGameJoin gameGameJoin(@NotNull QueryContext queryContext, @NotNull QueryOperator<Game> left, @NotNull QueryOperator<Game> right) {
        return new MergeGameJoin(queryContext, left, null, right);
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of();
    }

    @Override
    protected Stream<Game> operatorStream() {
        // TODO: Make this a Merge join
        Set<Integer> hashSet;
        if (rightId != null) {
            hashSet = rightId.stream().collect(Collectors.toSet());
        } else {
            assert rightGame != null;
            hashSet = rightGame.stream().map(Game::id).collect(Collectors.toSet());
        }
        return left.stream().filter(game -> hashSet.contains(game.id()));
    }

    @Override
    protected void estimateOperatorCost(ImmutableOperatorCost.@NotNull Builder operatorCost) {
        OperatorCost leftCost = left.getOperatorCost();
        OperatorCost rightCost = (rightId != null ? rightId : rightGame).getOperatorCost();

        operatorCost
                .estimateRows(Math.min(leftCost.estimateRows(), rightCost.estimateRows()))
                .estimatePageReads(0)
                .estimateDeserializations(0);
    }

    @Override
    public List<QueryOperator<?>> sources() {
        if (rightId != null) {
            return List.of(left, rightId);
        }
        assert rightGame != null;
        return List.of(left, rightGame);
    }

    @Override
    public String toString() {
        return "MergeJoin";
    }
}
