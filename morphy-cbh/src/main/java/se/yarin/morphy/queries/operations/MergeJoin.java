package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.IdObject;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MergeJoin<T extends IdObject> extends QueryOperator<T> {
    private final @NotNull QueryOperator<T> left;
    private final @NotNull QueryOperator<T> right;

    public MergeJoin(@NotNull QueryContext queryContext,
                     @NotNull QueryOperator<T> left,
                     @NotNull QueryOperator<T> right) {
        super(queryContext, left.hasFullData() || right.hasFullData());
        this.left = left;
        this.right = right;
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of();
    }

    @Override
    protected Stream<QueryData<T>> operatorStream() {
        // TODO: Make this a Merge join
        // TODO: Make sure that if any of the streams contains the full data, the output should have the full data
        Set<Integer> hashSet = right.stream().map(QueryData::id).collect(Collectors.toSet());
        return left.stream().filter(idObject -> hashSet.contains(idObject.id()));
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
    public List<QueryOperator<?>> sources() {
        return List.of(left, right);
    }

    @Override
    public String toString() {
        return "MergeJoin";
    }
}
