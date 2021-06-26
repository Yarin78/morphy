package se.yarin.morphy.queries.operations;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.metrics.MetricsProvider;
import se.yarin.morphy.queries.QueryContext;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HashJoin extends QueryOperator<Integer> {
    private final @NotNull QueryOperator<Integer> left, right;

    public HashJoin(@NotNull QueryContext queryContext, @NotNull QueryOperator<Integer> left, @NotNull QueryOperator<Integer> right) {
        super(queryContext);
        this.left = left;
        this.right = right;
    }

    @Override
    protected List<MetricsProvider> metricProviders() {
        return List.of();
    }

    @Override
    protected Stream<Integer> operatorStream() {
        // Build
        Set<Integer> hashSet = right.stream().collect(Collectors.toSet());
        // Probe
        return left.stream().filter(hashSet::contains);
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
        return "HashJoin";
    }
}
