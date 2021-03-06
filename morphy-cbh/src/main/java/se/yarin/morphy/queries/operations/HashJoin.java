package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;
import se.yarin.morphy.queries.QuerySortOrder;
import se.yarin.morphy.util.StreamUtil;

import java.util.List;
import java.util.stream.Stream;

public class HashJoin<T extends IdObject> extends QueryOperator<T> {
    private final @NotNull QueryOperator<T> left, right;

    public HashJoin(@NotNull QueryContext queryContext, @NotNull QueryOperator<T> left, @NotNull QueryOperator<T> right) {
        super(queryContext, left.hasFullData() || right.hasFullData());
        if (left.mayContainDuplicates() || right.mayContainDuplicates()) {
            // Disallow this for now as it's undefined how weights are handled
            throw new IllegalArgumentException("The sources of a hash join must not contain duplicates");
        }
        this.left = left;
        this.right = right;
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of();
    }

    @Override
    protected Stream<QueryData<T>> operatorStream() {
        return StreamUtil.hashJoin(left.stream(), right.stream(), QueryData.merger());
    }

    @Override
    public List<QueryOperator<?>> sources() {
        return List.of(left, right);
    }

    public @NotNull QuerySortOrder<T> sortOrder() {
        return left.sortOrder();
    }

    public boolean mayContainDuplicates() {
        return false;
    }

    @Override
    protected void estimateOperatorCost(ImmutableOperatorCost.@NotNull Builder operatorCost) {
        OperatorCost leftCost = left.getOperatorCost();
        OperatorCost rightCost = right.getOperatorCost();

        operatorCost
            .estimateRows(Math.min(leftCost.estimateRows(), rightCost.estimateRows()))
            .estimatePageReads(0)
            .estimateDeserializations(0);
    }

    @Override
    public String toString() {
        return "HashJoin";
    }
}
